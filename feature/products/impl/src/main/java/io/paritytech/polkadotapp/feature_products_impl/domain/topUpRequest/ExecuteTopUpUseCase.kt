package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.util.amountFromPlanks
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.OnboardingUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Outcome of a successful top-up claim. */
sealed interface TopUpClaimResult {
    /** The claimed funds matched the amount the product stated. */
    data object Exact : TopUpClaimResult

    /** A Coins top-up whose detected on-chain total [credited] differs from the stated amount. */
    data class Partial(val credited: Balance) : TopUpClaimResult
}

interface ExecuteTopUpUseCase {
    /**
     * Performs the top-up: onboards via the signer, or moves the coins into the user's coin set.
     * For a [TopUpSource.Coins] top-up, reports [TopUpClaimResult.Partial] when the
     * detected on-chain total differs from [amount]; otherwise [TopUpClaimResult.Exact].
     */
    suspend fun claim(source: TopUpSource, amount: Balance): Result<TopUpClaimResult>
}

class RealExecuteTopUpUseCase @Inject constructor(
    private val coinageTransferUseCase: CoinageTransferUseCase,
    private val onboardingUseCase: OnboardingUseCase,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
) : ExecuteTopUpUseCase {
    override suspend fun claim(source: TopUpSource, amount: Balance): Result<TopUpClaimResult> =
        when (source) {
            is TopUpSource.Onboard -> {
                val decimalAmount = chainAssetProvider.asset().amountFromPlanks(amount)
                onboardingUseCase.onboard(decimalAmount, source.signerSource).map { TopUpClaimResult.Exact }
            }

            is TopUpSource.Coins -> claimCoins(source.coinKeys, amount)
        }

    private suspend fun claimCoins(coinKeys: List<CoinPrivateKey>, expectedAmount: Balance): Result<TopUpClaimResult> =
        runCatching {
            val outcome = coinageTransferUseCase(transferCoins = true, coinKeys = coinKeys, pastDetection = null)
                .first { it is CoinageTransferDetection.Transferred || it is CoinageTransferDetection.Error }

            if (outcome !is CoinageTransferDetection.Transferred) {
                error("Failed to move coins into the user's coin set: $outcome")
            }

            if (outcome.amount < expectedAmount) TopUpClaimResult.Partial(outcome.amount) else TopUpClaimResult.Exact
        }
}
