package io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts

import android.webkit.WebView
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreLoadError
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreProductsInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreProductsViewModelTest {
    private val interactor: ExploreProductsInteractor = mock()
    private val router: ProductsRouter = mock()
    private val provider: BrowserWebViewProvider = mock()
    private val webView: WebView = mock()

    private val mainDispatcher = StandardTestDispatcher()

    private val hostFailure = ExploreLoadError.HostUnresolved(IllegalStateException("no tld"))
    private val setupFailure = ExploreLoadError.SetupFailed(IllegalStateException("no content"))

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows the page when the attempt succeeds`() = runTest(mainDispatcher) {
        withPage()
        withFirstAttempt(succeeding())

        val viewModel = createViewModel()
        runCurrent()

        assertEquals(LoadingState.Loaded(ExploreProductsContent.Page(webView)), viewModel.state.value)
    }

    @Test
    fun `a host failure shows the error and skip leaves Browse empty`() = runTest(mainDispatcher) {
        withFirstAttempt(failingWith(hostFailure))
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(LoadingState.Error(hostFailure), viewModel.state.value)

        viewModel.onSkip()
        runCurrent()

        assertEquals(LoadingState.Loaded(ExploreProductsContent.Skipped), viewModel.state.value)
    }

    @Test
    fun `retry starts a new attempt`() = runTest(mainDispatcher) {
        withPage()
        withFirstAttempt(failingWith(hostFailure))
        withRetryAttempt(succeeding())
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onRetry()
        runCurrent()

        assertEquals(LoadingState.Loaded(ExploreProductsContent.Page(webView)), viewModel.state.value)
    }

    @Test
    fun `a setup failure stays loading until 500 ms then shows the error`() = runTest(mainDispatcher) {
        withFirstAttempt(failingWith(setupFailure))
        val viewModel = createViewModel()

        advanceTimeBy(JUST_BEFORE_FLOOR)
        runCurrent()
        assertEquals(LoadingState.Loading, viewModel.state.value)

        advanceTimeBy(SETUP_ERROR_FLOOR - JUST_BEFORE_FLOOR)
        runCurrent()
        assertEquals(LoadingState.Error(setupFailure), viewModel.state.value)
    }

    private fun createViewModel() = ExploreProductsViewModel(interactor = interactor, router = router)

    private suspend fun withPage() {
        whenever(provider.getWebView()).thenReturn(webView)
    }

    private fun withFirstAttempt(attempt: Flow<Result<BrowserWebViewProvider>>) {
        whenever(with(any<StalenessReportCollector>()) { interactor.openExplore(eq(false), any()) }).thenReturn(attempt)
    }

    private fun withRetryAttempt(attempt: Flow<Result<BrowserWebViewProvider>>) {
        whenever(with(any<StalenessReportCollector>()) { interactor.openExplore(eq(true), any()) }).thenReturn(attempt)
    }

    private fun succeeding(): Flow<Result<BrowserWebViewProvider>> = flow {
        emit(Result.success(provider))
        awaitCancellation()
    }

    private fun failingWith(error: ExploreLoadError): Flow<Result<BrowserWebViewProvider>> = flowOf(Result.failure(error))

    private companion object {
        val SETUP_ERROR_FLOOR = 500.milliseconds
        val JUST_BEFORE_FLOOR = SETUP_ERROR_FLOOR - 1.milliseconds
    }
}
