package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain

import io.paritytech.polkadotapp.chains.util.planksFromAmount
import io.paritytech.polkadotapp.common.domain.validation.Validation
import io.paritytech.polkadotapp.common.domain.validation.ValidationProcess
import io.paritytech.polkadotapp.common.domain.validation.ValidationResult
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import javax.inject.Inject

/**
 * There is nothing left to confirm here. Balance the privacy strategy holds back is not offered for
 * spending at all, so an amount is either covered by what is available or it is not.
 */
class SendValidation @Inject constructor(
    private val totalBalanceUseCase: TotalBalanceUseCase,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider
) : Validation<SendValidationPayload> {
    context(validationProcess: ValidationProcess)
    override suspend fun validate(payload: SendValidationPayload): ValidationResult<SendValidationPayload> {
        val balance = totalBalanceUseCase.getBalance()
            .getOrElse { return ValidationResult.Error(Throwable("Can't fetch balance")) }

        val asset = chainAssetProvider.asset()
        val transferAmountPlanks = payload.value.planksFromAmount(asset.precision)

        return if (transferAmountPlanks <= balance.spendable) {
            ValidationResult.Success(payload)
        } else {
            ValidationResult.Error(Throwable("Amount exceeds available balance"))
        }
    }
}
