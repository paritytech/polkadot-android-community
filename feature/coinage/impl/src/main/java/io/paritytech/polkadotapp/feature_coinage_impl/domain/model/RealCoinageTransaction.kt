package io.paritytech.polkadotapp.feature_coinage_impl.domain.model

import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.VoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.DerivationIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RingVrfIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogD
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogE
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction.Stage

// TODO: ensureActive throws, migrate all methods to Result
class RealCoinageTransaction(
    private val coinAllocator: CoinAllocator,
    private val voucherAllocator: VoucherAllocator,
    private val coinRepository: CoinRepository,
    private val voucherRepository: VoucherRepository,
) : CoinageTransaction {
    private val operations = mutableListOf<Operation>()
    private var concluded = false

    override suspend fun mintCoins(valueExponents: List<ValueExponent>): Result<List<Coin>> {
        ensureActive()
        if (valueExponents.isEmpty()) return Result.success(emptyList())

        return coinAllocator.allocateAll(valueExponents)
            .onSuccess { coins -> operations.add(MintCoinsOperation(coins.map { it.derivationIndex })) }
    }

    override suspend fun mintVoucher(valueExponent: ValueExponent): Result<RecyclerVoucher> {
        ensureActive()

        return voucherAllocator.allocate(valueExponent)
            .onSuccess { voucher -> operations.add(MintVoucherOperation(listOf(voucher.ringVrfKeyIndex))) }
    }

    override suspend fun consumeCoins(coins: List<Coin>) {
        ensureActive()
        val indices = coins.map { it.derivationIndex }.ifEmpty { return }
        coinRepository.setSpentStateByDerivationIndices(indices, Coin.SpentState.SPENT_LOCALLY)
        operations.add(ConsumeOperation(indices))
    }

    override suspend fun handOffCoins(coins: List<Coin>) {
        ensureActive()
        val indices = coins.map { it.derivationIndex }.ifEmpty { return }
        coinRepository.setSpentStateByDerivationIndices(indices, Coin.SpentState.SPENT_LOCALLY)
        operations.add(HandOffOperation(indices))
    }

    override suspend fun useVouchers(vouchers: List<RecyclerVoucher>) {
        ensureActive()
        val indices = vouchers.map { it.ringVrfKeyIndex }.ifEmpty { return }
        voucherRepository.setUsageStateByRingVrfKeyIndices(indices, RecyclerVoucher.UsageState.USED_LOCALLY)
        operations.add(UseVouchersOperation(indices))
    }

    override suspend fun commit() {
        ensureActive()
        coinageLogD("CoinageTransaction committing operations:${operations.describe()}")
        operations.forEach { it.commit() }
        concluded = true
    }

    override suspend fun rollback(stage: Stage, cause: Throwable) {
        ensureActive()
        coinageLogE("CoinageTransaction rolling back at stage=$stage operations:${operations.describe()}", cause)
        operations.asReversed().forEach { it.rollback(stage) }
        concluded = true
    }

    private fun ensureActive() = check(!concluded) { "CoinageTransaction has already been concluded" }

    private fun List<Operation>.describe() = joinToString(prefix = "\n", separator = "\n") { " - ${it.describe()}" }

    private interface Operation {
        /** Human-readable summary of the mutation, e.g. `mint vouchers [1, 2, 3]`. */
        fun describe(): String

        suspend fun commit()

        suspend fun rollback(stage: Stage)
    }

    private inner class MintCoinsOperation(private val indices: List<DerivationIndex>) : Operation {
        override fun describe() = "mint coins $indices"

        override suspend fun commit() = Unit

        override suspend fun rollback(stage: Stage) = coinAllocator.deallocate(indices)
    }

    private inner class MintVoucherOperation(private val indices: List<RingVrfIndex>) : Operation {
        override fun describe() = "mint vouchers $indices"

        override suspend fun commit() = Unit

        override suspend fun rollback(stage: Stage) = voucherAllocator.deallocate(indices)
    }

    private inner class ConsumeOperation(private val indices: List<DerivationIndex>) : Operation {
        override fun describe() = "consume coins $indices"

        override suspend fun commit() = coinRepository.setSpentStateByDerivationIndices(indices, Coin.SpentState.SPENT_ON_CHAIN)

        override suspend fun rollback(stage: Stage) = coinRepository.setSpentStateByDerivationIndices(indices, Coin.SpentState.NOT_SPENT)
    }

    private inner class HandOffOperation(private val indices: List<DerivationIndex>) : Operation {
        override fun describe() = "hand off coins $indices"

        override suspend fun commit() = Unit

        override suspend fun rollback(stage: Stage) {
            // Once the memo is shared the recipient holds the keys, so the spend stays even on failure.
            if (stage != Stage.MEMO_SHARED) {
                coinRepository.setSpentStateByDerivationIndices(indices, Coin.SpentState.NOT_SPENT)
            }
        }
    }

    private inner class UseVouchersOperation(private val indices: List<RingVrfIndex>) : Operation {
        override fun describe() = "use vouchers $indices"

        override suspend fun commit() = voucherRepository.setUsageStateByRingVrfKeyIndices(indices, RecyclerVoucher.UsageState.USED_ON_CHAIN)

        override suspend fun rollback(stage: Stage) = voucherRepository.setUsageStateByRingVrfKeyIndices(indices, RecyclerVoucher.UsageState.NOT_USED)
    }
}
