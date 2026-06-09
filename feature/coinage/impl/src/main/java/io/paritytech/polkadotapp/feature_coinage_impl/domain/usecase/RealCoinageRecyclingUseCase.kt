package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.bandersnatch_crypto.memberKey
import io.paritytech.polkadotapp.bandersnatch_crypto.sign
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.VoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageRecyclingUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.coinage
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.loadRecyclerWithCoin
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.VoucherRingDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.MultiExtrinsicBuilder
import javax.inject.Inject

class RealCoinageRecyclingUseCase @Inject constructor(
    private val coinRepository: CoinRepository,
    private val voucherAllocator: VoucherAllocator,
    private val voucherRepository: VoucherRepository,
    private val voucherRingDerivation: VoucherRingDerivation,
    private val coinageTransactionOrigins: CoinageTransactionOrigins,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
    private val chainRegistry: ChainRegistry,
    private val extrinsicService: ExtrinsicService,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider
) : CoinageRecyclingUseCase {
    override suspend fun invoke(): Result<Unit> {
        val recyclingAge = coinRepository.getCoinRecyclingAge()
        val coinsToRecycle = coinRepository.getActiveCoinsWithKnownAge(minAge = recyclingAge)

        return if (coinsToRecycle.isEmpty()) {
            Result.success(Unit)
        } else {
            recycle(coinsToRecycle)
        }
    }

    override suspend fun recycle(coins: List<Coin>): Result<Unit> {
        val spentCoins = coins.withSpentState(Coin.SpentState.SPENT_LOCALLY)
        coinRepository.saveAll(spentCoins)

        val coinsWithVouchers = coins.allocateVouchersForCoins()

        val chainId = chainAssetProvider.chainId()
        return chainConnectionRefCounter.withConnectionEnabled(chainId, "CoinageRecycling") {
            val chain = chainRegistry.getChain(chainId)

            performRecycleTrackingStatuses(chain, coinsWithVouchers)
        }
    }

    private suspend fun performRecycleTrackingStatuses(
        chain: Chain,
        coinsWithVouchers: List<Pair<Coin, RecyclerVoucher>>
    ): Result<Unit> = runCatching {
        extrinsicService.submitExtrinsicsAndAwaitInBlock(chain) {
            coinsWithVouchers.forEach { (coin, voucher) ->
                buildLoadRecyclerExtrinsic(coin, voucher)
            }
        }
            .getOrThrow()
    }
        .onSuccess {
            val failedCoinsWithVouchers = mutableListOf<Pair<Coin, RecyclerVoucher>>()

            it.forEachIndexed { index, result ->
                if (result.isFailure) {
                    failedCoinsWithVouchers.add(coinsWithVouchers[index])
                }
            }
            rollback(failedCoinsWithVouchers)
        }
        .onFailure {
            rollback(coinsWithVouchers)
        }
        .coerceToUnit()

    private suspend fun List<Coin>.allocateVouchersForCoins() = mapNotNull { coin ->
        coin to (voucherAllocator.allocate(coin.valueExponent).getOrNull() ?: return@mapNotNull null)
    }

    private suspend fun rollback(coinsWithVouchers: List<Pair<Coin, RecyclerVoucher>>) {
        val rolledBackCoins = coinsWithVouchers.map { it.first }.withSpentState(Coin.SpentState.NOT_SPENT)
        coinRepository.saveAll(rolledBackCoins)

        val rolledBackVouchers = coinsWithVouchers.map { it.second.ringVrfKeyIndex }
        voucherRepository.removeVouchers(rolledBackVouchers)
    }

    private fun List<Coin>.withSpentState(state: Coin.SpentState) = map { it.copy(spentState = state) }

    context(MultiExtrinsicBuilder)
    private suspend fun buildLoadRecyclerExtrinsic(coin: Coin, voucher: RecyclerVoucher) {
        val keypair = voucherRingDerivation.deriveBandersnatch(voucher.ringVrfKeyIndex)
        val origin = coinageTransactionOrigins.createAsCoinOrigin(coin)

        extrinsic(origin = origin) {
            coinage.loadRecyclerWithCoin(
                memberKey = keypair.memberKey().value,
                proofOfOwnership = keypair.sign(coin.accountId.value)
            )
        }
    }
}
