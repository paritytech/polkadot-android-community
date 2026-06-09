package io.paritytech.polkadotapp.feature_products_impl.presentation.topUpRequest

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.withAmount
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.flowOf
import io.paritytech.polkadotapp.common.utils.inBackground
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.common.utils.shareInBackground
import io.paritytech.polkadotapp.common.utils.withLoading
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.TopUpRequestContext
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.TopUpRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.TopUpRequestInteractor
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
class TopUpRequestViewModel @Inject constructor(
    private val router: ProductsRouter,
    private val context: TopUpRequestContext,
    private val holder: TopUpRequestContextHolder,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val tokenAmountMapper: TokenAmountMapper,
    private val interactor: TopUpRequestInteractor,
) : BaseViewModel(), TopUpRequestContract {
    private val claiming = MutableStateFlow(false)

    // Set when a Coins top-up's detected on-chain amount differs from the amount the product stated.
    private val amountMismatch = MutableStateFlow(false)

    private val amountFlow = flowOf {
        val asset = chainAssetProvider.asset()
        tokenAmountMapper.mapFrom(asset.withAmount(context.amount))
    }.shareInBackground()

    init {
        launchUnit {
            amountMismatch.value = interactor.isAmountMismatched(context.source, context.amount)
        }
    }

    override val state: StateFlow<LoadingState<TopUpRequestUiState>> =
        combine(amountFlow, claiming, amountMismatch) { amount, isClaiming, mismatch ->
            TopUpRequestUiState(
                productId = context.productId.value,
                amount = amount,
                claiming = isClaiming,
                amountMismatch = mismatch,
            )
        }
            .inBackground()
            .withLoading()
            .stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)

    override fun onClaimClicked() = launchUnit {
        if (claiming.value) return@launchUnit
        claiming.value = true

        interactor.claim(context.source, context.amount).fold(
            onSuccess = { context.deliverClaimed() },
            onFailure = { context.deliverFailed(it) },
        )
        router.back()
    }

    override fun onCleared() {
        super.onCleared()
        holder.clear()
    }
}
