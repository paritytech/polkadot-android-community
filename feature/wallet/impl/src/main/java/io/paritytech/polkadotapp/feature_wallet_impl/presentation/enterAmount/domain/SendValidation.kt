package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.withAsset
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
    context(validationProcess: ValidationProcess)
    override suspend fun validate(payload: SendValidationPayload): ValidationResult<SendValidationPayload> {
        val balance = totalBalanceUseCase.getBalance()
            .getOrElse { return ValidationResult.Error(SendValidationFailure.BalanceUnavailable()) }

        val asset = chainAssetProvider.asset()
        val transferAmountPlanks = payload.value.planksFromAmount(asset.precision)

        if (transferAmountPlanks <= balance.availablePrivate) return ValidationResult.Success(payload)

        val gainingPrivacy = balance.gainingPrivacy
        val reachable = balance.availablePrivate + gainingPrivacy.amount

        // Either the strategy will not part with what it is holding, or even that would not cover the
        // amount. Both are the same answer to the user: this cannot be sent.
        if (!gainingPrivacy.canSpendWithConfirmation || transferAmountPlanks > reachable) {
            return ValidationResult.Error(SendValidationFailure.AmountNoLongerAvailable())
        }

        val action = ConfirmGainingPrivacySpendUserAction(
            totalTransfer = tokenAmountMapper.mapFrom(transferAmountPlanks.withAsset(asset)),
        )

        return when (validationProcess.presentUserInput(action)) {
            ConfirmGainingPrivacySpendDecision.SendAnyway -> ValidationResult.Success(payload)
            ConfirmGainingPrivacySpendDecision.Cancel -> ValidationResult.Aborted
        }
    }
}

/**
 * [totalTransfer] is the amount the user asked for. Part of it can only come from funds the privacy system
 * has not finished processing, which is what the confirmation is about.
 */
data class ConfirmGainingPrivacySpendUserAction(
    val totalTransfer: TokenAmountModel,
) : ValidationUserInputAction<ConfirmGainingPrivacySpendDecision>

/**
 * Why a send was refused before it was ever submitted.
 *
 * Typed rather than a message, so the screen picks the wording. Both cases mean the balance moved after the
 * input accepted the amount — the recycler takes coins out of reach on its own schedule — so neither is the
 * user asking for more than they have; the input already refuses that.
 */
sealed class SendValidationFailure : Exception() {
    class BalanceUnavailable : SendValidationFailure()
    class AmountNoLongerAvailable : SendValidationFailure()
}

/**
 * There is no "send only what is spendable" any more: the user asked for an amount, and silently sending a
 * smaller one is a worse answer than telling them it cannot be sent.
 */
sealed interface ConfirmGainingPrivacySpendDecision {
    data object SendAnyway : ConfirmGainingPrivacySpendDecision
    data object Cancel : ConfirmGainingPrivacySpendDecision
}
