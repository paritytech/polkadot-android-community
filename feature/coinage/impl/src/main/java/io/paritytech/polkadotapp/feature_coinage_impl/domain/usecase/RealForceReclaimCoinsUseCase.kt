package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.filterNotNull
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.formatExponentsToBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ForceReclaimCoinsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ReclaimOutcome
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.CoinKeypairDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import timber.log.Timber
import javax.inject.Inject

class RealForceReclaimCoinsUseCase @Inject constructor(
    private val coinRepository: CoinRepository,
    private val coinKeypairDerivation: CoinKeypairDerivation,
    private val coinageTransferSubmissionUseCase: CoinageTransferSubmissionUseCase,
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
) : ForceReclaimCoinsUseCase {
    override suspend operator fun invoke(): Result<ReclaimOutcome> {
        // Both spent states are candidates: SPENT_LOCALLY (optimistically spent) and SPENT_ON_CHAIN — the latter
        // because a fork rollback can resurrect a coin that tracking marked spent-on-chain when it disappeared.
        // The decision is on-chain presence, not the stored state, so a still-absent coin is simply skipped.
        val reclaimCandidates = coinRepository.getCoinsWithSpentState(Coin.SpentState.SPENT_LOCALLY) +
            coinRepository.getCoinsWithSpentState(Coin.SpentState.SPENT_ON_CHAIN)
        Timber.d("Force reclaim: ${reclaimCandidates.size} candidates (SPENT_LOCALLY + SPENT_ON_CHAIN) ${reclaimCandidates.logSummary()}")

        if (reclaimCandidates.isEmpty()) return Result.success(ReclaimOutcome.NothingToReclaim)

        return coinRepository.fetchCoinsInfoFor(chainAssetProvider.chainId(), reclaimCandidates.map { it.accountId })
            .flatMap { coinsInfo -> reclaim(reclaimCandidates, coinsInfo.filterNotNull()) }
    }

    private suspend fun reclaim(
        candidates: List<Coin>,
        presentCoinsInfo: Map<AccountId, OnChainCoinInfo>,
    ): Result<ReclaimOutcome> {
        val presentCoins = candidates.filter { it.accountId in presentCoinsInfo }
        Timber.d("Force reclaim: ${presentCoins.size}/${candidates.size} candidates present on-chain ${presentCoins.logSummary()}")

        if (presentCoins.isEmpty()) return Result.success(ReclaimOutcome.NothingToReclaim)

        val keyPairs = coinKeypairDerivation.deriveKeypairs(presentCoins.map { it.derivationIndex })

        return coinageTransferSubmissionUseCase(keyPairs, presentCoinsInfo)
            .flatMap { reclaimedBalance(presentCoinsInfo.values) }
            .onSuccess { balance ->
                Timber.d("Force reclaim: reclaimed ${presentCoins.size} coins ${presentCoins.logSummary()}, total=$balance")
            }
            .logFailure("Force reclaim: failed for ${presentCoins.logSummary()}")
            .map { balance -> ReclaimOutcome.Reclaimed(balance) }
    }

    private suspend fun reclaimedBalance(coinsInfo: Collection<OnChainCoinInfo>): Result<Balance> {
        return coinageBalanceConverterUseCase.create().map { context ->
            context.formatExponentsToBalance(coinsInfo.map { ValueExponent(it.value) })
        }
    }

    private fun List<Coin>.logSummary(): String =
        joinToString(prefix = "[", postfix = "]") { "index=${it.derivationIndex},exp=${it.valueExponent.value}" }
}
