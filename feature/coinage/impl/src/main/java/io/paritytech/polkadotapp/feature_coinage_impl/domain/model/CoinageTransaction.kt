package io.paritytech.polkadotapp.feature_coinage_impl.domain.model

import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.VoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import javax.inject.Inject

/**
 * Single seam for every coinage mutation. Each mutation eagerly applies its forward change (so coins are marked
 * `SPENT_LOCALLY` / vouchers `USED_LOCALLY` before the extrinsic is submitted) and registers its exact inverse, so
 * [commit] and [rollback] are symmetric by construction. Once concluded (committed or rolled back) the transaction
 * rejects any further mutation.
 */
interface CoinageTransaction {
    enum class Stage { PREPARATION, MEMO_SHARED }

    interface Factory {
        fun newTransaction(): CoinageTransaction
    }

    /** Allocate brand-new output coins (recipient / change). Removed on rollback, untouched on commit. */
    suspend fun mintCoins(valueExponents: List<ValueExponent>): Result<List<Coin>>

    /** Allocate a brand-new voucher. Removed on rollback, untouched on commit. */
    suspend fun mintVoucher(valueExponent: ValueExponent): Result<RecyclerVoucher>

    /** Mark existing coins our own extrinsic destroys (e.g. a coin being split): `SPENT_ON_CHAIN` on commit, `NOT_SPENT` on rollback. */
    suspend fun consumeCoins(coins: List<Coin>)

    /** Mark existing coins handed to a recipient (keys shared in the memo): left `SPENT_LOCALLY` on commit, reverted only before the memo is shared. */
    suspend fun handOffCoins(coins: List<Coin>)

    /** Mark vouchers used: `USED_ON_CHAIN` on commit, `NOT_USED` on rollback. */
    suspend fun useVouchers(vouchers: List<RecyclerVoucher>)

    suspend fun commit()

    suspend fun rollback(stage: Stage, cause: Throwable)
}

class CoinageTransactionFactory @Inject constructor(
    private val coinRepository: CoinRepository,
    private val voucherRepository: VoucherRepository,
    private val coinAllocator: CoinAllocator,
    private val voucherAllocator: VoucherAllocator,
) : CoinageTransaction.Factory {
    override fun newTransaction(): CoinageTransaction {
        return RealCoinageTransaction(coinAllocator, voucherAllocator, coinRepository, voucherRepository)
    }
}

/** Runs [block] against a fresh transaction, committing on success and rolling back on failure. */
suspend fun <R> CoinageTransaction.Factory.runInTransaction(block: suspend CoinageTransaction.() -> R): Result<R> {
    val transaction = newTransaction()

    return runCancellableCatching {
        transaction.block().also { transaction.commit() }
    }.onFailure {
        transaction.rollback(CoinageTransaction.Stage.PREPARATION, it)
    }
}

/** Single-coin convenience over [CoinageTransaction.mintCoins]. */
suspend fun CoinageTransaction.mintCoin(valueExponent: ValueExponent): Result<Coin> =
    mintCoins(listOf(valueExponent)).map { it.single() }

/** Single-coin convenience over [CoinageTransaction.consumeCoins]. */
suspend fun CoinageTransaction.consumeCoin(coin: Coin) = consumeCoins(listOf(coin))

/** Mint output coins and immediately hand them to the recipient. Recipient coins always do both. */
suspend fun CoinageTransaction.mintAndHandOffCoins(valueExponents: List<ValueExponent>): Result<List<Coin>> =
    mintCoins(valueExponents).onSuccess { handOffCoins(it) }
