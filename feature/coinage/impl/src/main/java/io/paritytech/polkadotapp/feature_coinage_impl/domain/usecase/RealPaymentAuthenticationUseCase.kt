package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.util.planksFromAmount
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.PaymentAuthenticationUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.tools_authentication_api.domain.BiometricsService
import java.math.BigDecimal
import javax.inject.Inject

private val MAX_THRESHOLD_CASH = BigDecimal(50)

class RealPaymentAuthenticationUseCase @Inject constructor(
    private val totalBalanceUseCase: TotalBalanceUseCase,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val biometricsService: BiometricsService,
) : PaymentAuthenticationUseCase {
    override suspend fun authenticateIfRequired(amount: Balance): Result<Unit> {
        val isRequired = totalBalanceUseCase.getBalance()
            .logFailure("Failed to read the balance, asking for device authentication")
            .fold(
                onSuccess = { balance -> amount >= authenticationThreshold(balance) },
                onFailure = { true },
            )

        return if (isRequired) biometricsService.performOneTimeAuthentication() else Result.success(Unit)
    }

    private suspend fun authenticationThreshold(balance: CoinageBalance): Balance {
        val halfOfBalance = balance.total / 2
        val maxThreshold = chainAssetProvider.asset().planksFromAmount(MAX_THRESHOLD_CASH)

        return minOf(halfOfBalance, maxThreshold)
    }
}
