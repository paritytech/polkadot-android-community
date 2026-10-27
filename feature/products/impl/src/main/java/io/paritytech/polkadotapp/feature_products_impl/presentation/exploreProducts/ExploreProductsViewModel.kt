package io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.loading.dataOrNull
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReport
import io.paritytech.polkadotapp.common.utils.withLoading
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.presentation.SpaBrowserPayload
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreLoadError
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreProductsInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExploreProductsViewModel @Inject constructor(
    private val interactor: ExploreProductsInteractor,
    private val router: ProductsRouter,
) : BaseViewModel(), ExploreProductsContract {
    override val stalenessReport = StalenessReport(this)

    private val retryCount = MutableStateFlow(0)
    private val hostErrorSkipped = MutableStateFlow(false)

    private val session: StateFlow<LoadingState<BrowserWebViewProvider>> = retryCount
        .flatMapLatest { count -> attempt(afterRetry = count > 0).withLoading("ExploreProducts") }
        .stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)

    override val state: StateFlow<LoadingState<ExploreProductsContent>> = combine(session, hostErrorSkipped) { loading, skipped ->
        when (loading) {
            LoadingState.Loading -> LoadingState.Loading
            is LoadingState.Loaded -> LoadingState.Loaded(ExploreProductsContent.Page(loading.data.getWebView()))
            is LoadingState.Error -> if (loading.exception is ExploreLoadError.HostUnresolved && skipped) {
                LoadingState.Loaded(ExploreProductsContent.Skipped)
            } else {
                loading
            }
        }
    }.stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)

    override fun onRetry() {
        retryCount.update { it + 1 }
    }

    override fun onSkip() {
        hostErrorSkipped.value = true
    }

    override fun pauseConnections() {
        session.value.dataOrNull?.pauseConnections()
    }

    override fun resumeConnections() {
        session.value.dataOrNull?.resumeConnections()
    }

    private fun onProductSelected(productId: ProductId) {
        launch { router.openSpaBrowser(SpaBrowserPayload.ByProductId(productId.value)) }
    }

    private fun attempt(afterRetry: Boolean): Flow<Result<BrowserWebViewProvider>> {
        val opening = with(stalenessReport) { interactor.openExplore(afterRetry, ::onProductSelected) }

        return combine(opening, setupErrorFloor()) { result, floorPassed ->
            result.takeIf { floorPassed || it.exceptionOrNull() !is ExploreLoadError.SetupFailed }
        }.filterNotNull().distinctUntilChanged()
    }

    private fun setupErrorFloor(): Flow<Boolean> = flow {
        emit(false)
        delay(SETUP_ERROR_MIN_LOADING)
        emit(true)
    }

    private companion object {
        val SETUP_ERROR_MIN_LOADING = 500.milliseconds
    }
}
