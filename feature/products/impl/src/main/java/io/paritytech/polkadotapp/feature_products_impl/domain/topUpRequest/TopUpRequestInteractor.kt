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

interface TopUpRequestInteractor {
    /**
     * For a [TopUpRequestContext.Source.Coins] top-up, detects the coins on-chain without
     * transferring and reports whether their total differs from [expectedAmount]. Always `false`
     * for other sources.
     */
    suspend fun isAmountMismatched(source: TopUpRequestContext.Source, expectedAmount: Balance): Boolean

    /** Performs the top-up: onboards via the signer, or moves the coins into the user's coin set. */
    suspend fun claim(source: TopUpRequestContext.Source, amount: Balance): Result<Unit>
}

class RealTopUpRequestInteractor @Inject constructor(
    private val coinageTransferUseCase: CoinageTransferUseCase,
    private val onboardingUseCase: OnboardingUseCase,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
) : TopUpRequestInteractor {
    // Detection from the cross-check, reused when claiming so the coins aren't re-detected on-chain.
    private var lastDetection: CoinageTransferDetection.Detected? = null

    override suspend fun isAmountMismatched(source: TopUpRequestContext.Source, expectedAmount: Balance): Boolean {
        val coinKeys = (source as? TopUpRequestContext.Source.Coins)?.coinKeys ?: return false

        val detected = coinageTransferUseCase(transferCoins = false, coinKeys = coinKeys, pastDetection = null)
            .first { it is CoinageTransferDetection.Detected || it is CoinageTransferDetection.Error }
            as? CoinageTransferDetection.Detected
            ?: return false

        lastDetection = detected
        return detected.amount != expectedAmount
    }

    override suspend fun claim(source: TopUpRequestContext.Source, amount: Balance): Result<Unit> = when (source) {
        is TopUpRequestContext.Source.Onboard -> {
            val decimalAmount = chainAssetProvider.asset().amountFromPlanks(amount)
            onboardingUseCase.onboard(decimalAmount, source.signerSource)
        }

        is TopUpRequestContext.Source.Coins -> transferCoins(source.coinKeys)
    }

    private suspend fun transferCoins(coinKeys: List<CoinPrivateKey>): Result<Unit> = runCatching {
        val outcome = coinageTransferUseCase(transferCoins = true, coinKeys = coinKeys, pastDetection = lastDetection)
            .first { it is CoinageTransferDetection.Transferred || it is CoinageTransferDetection.Error }

        if (outcome !is CoinageTransferDetection.Transferred) {
            error("Failed to move coins into the user's coin set: $outcome")
        }
    }
}
