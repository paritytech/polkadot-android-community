package io.paritytech.polkadotapp.feature_products_impl.presentation.paymentRequest

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.withAmount
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.inBackground
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.common.utils.withLoading
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.PaymentAuthenticationUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.paymentRequest.PaymentRequestContext
import io.paritytech.polkadotapp.feature_products_impl.domain.paymentRequest.PaymentRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.domain.paymentRequest.PaymentRequestStep
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PaymentRequestViewModel @Inject constructor(
    private val router: ProductsRouter,
    private val context: PaymentRequestContext,
    private val holder: PaymentRequestContextHolder,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val tokenAmountMapper: TokenAmountMapper,
    private val paymentAuthenticationUseCase: PaymentAuthenticationUseCase,
) : BaseViewModel(), PaymentRequestContract {
    private val stepIndex = MutableStateFlow(0)
    private val approving = MutableStateFlow(false)

    override val state: StateFlow<LoadingState<PaymentRequestUiState>> =
        combine(stepIndex, approving) { index, isApproving ->
            PaymentRequestUiState(
                productId = context.productId.value,
                amount = tokenAmountMapper.mapFrom(chainAssetProvider.asset().withAmount(context.amount)),
                step = context.steps[index],
                approving = isApproving,
            )
        }
            .withLoading("PaymentRequest")
            .inBackground()
            .stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)

    override fun onApproveClicked() = launchUnit {
        if (approving.value) return@launchUnit

        approving.value = true

        authorizeStep(context.steps[stepIndex.value])
            .onAuthenticated {
                if (stepIndex.value < context.steps.lastIndex) {
                    stepIndex.value += 1
                } else {
                    context.deliverApproved()
                    router.back()
                }
            }

        approving.value = false
    }

    override fun onRejectClicked() = launchUnit {
        context.deliverRejected()
        router.back()
    }

    private suspend fun authorizeStep(step: PaymentRequestStep): Result<Unit> = when (step) {
        PaymentRequestStep.Confirm -> paymentAuthenticationUseCase.authenticateIfRequired(context.amount)
        PaymentRequestStep.PrivacyWarning -> Result.success(Unit)
    }

    override fun onCleared() {
        super.onCleared()
        holder.clear()
    }
}
