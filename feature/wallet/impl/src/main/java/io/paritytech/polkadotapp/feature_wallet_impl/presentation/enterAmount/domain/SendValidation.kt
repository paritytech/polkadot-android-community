package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.withAsset
import io.paritytech.polkadotapp.chains.util.amountFromPlanks
import io.paritytech.polkadotapp.chains.util.planksFromAmount
import io.paritytech.polkadotapp.common.domain.validation.Validation
import io.paritytech.polkadotapp.common.domain.validation.ValidationProcess
import io.paritytech.polkadotapp.common.domain.validation.ValidationResult
import io.paritytech.polkadotapp.common.domain.validation.ValidationUserInputAction
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import javax.inject.Inject

class SendValidation @Inject constructor(
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val tokenAmountMapper: TokenAmountMapper,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider
) : Validation<SendValidationPayload> {
    context(ValidationProcess)
    override suspend fun validate(payload: SendValidationPayload): ValidationResult<SendValidationPayload> {
        val balance = totalBalanceUseCase.getBalance().getOrElse { return ValidationResult.Error(Throwable("Can't fetch balance")) }

        val asset = chainAssetProvider.asset()
        val transferAmountPlanks = payload.value.planksFromAmount(asset.precision)
        val securedPlanks = balance.spendableBalance.secured

        if (transferAmountPlanks <= securedPlanks) return ValidationResult.Success(payload)

        val action = ConfirmDegradedVouchersUserAction(
            totalTransfer = tokenAmountMapper.mapFrom(transferAmountPlanks.withAsset(asset)),
            secured = tokenAmountMapper.mapFrom(securedPlanks.withAsset(asset)),
            degraded = tokenAmountMapper.mapFrom((transferAmountPlanks - securedPlanks).withAsset(asset)),
        )

        return when (presentUserInput(action)) {
            ConfirmDegradedVouchersDecision.SendPrivatelyOnly ->
                ValidationResult.Success(payload.copy(value = securedPlanks.amountFromPlanks(asset.precision)))

            ConfirmDegradedVouchersDecision.SendWithDegraded -> ValidationResult.Success(payload)

            ConfirmDegradedVouchersDecision.Cancel -> ValidationResult.Aborted
        }
    }
}

class ConfirmDegradedVouchersUserAction(
    val totalTransfer: TokenAmountModel,
    val secured: TokenAmountModel,
    val degraded: TokenAmountModel,
) : ValidationUserInputAction<ConfirmDegradedVouchersDecision>

sealed interface ConfirmDegradedVouchersDecision {
    data object SendPrivatelyOnly : ConfirmDegradedVouchersDecision
    data object SendWithDegraded : ConfirmDegradedVouchersDecision
    data object Cancel : ConfirmDegradedVouchersDecision
}
