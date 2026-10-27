package io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts

import android.webkit.WebView
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreLoadError
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreProductsInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreSetupFailure
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreProductsViewModelTest {
    private val interactor: ExploreProductsInteractor = mock()
    private val router: ProductsRouter = mock()
    private val provider: BrowserWebViewProvider = mock()
    private val webView: WebView = mock()

    private val testScope = TestScope()
    private val mainDispatcher = StandardTestDispatcher(testScope.testScheduler)

    private val hostFailure = ExploreLoadError.HostUnresolved(IllegalStateException("no tld"))
    private val setupFailure = ExploreLoadError.SetupFailed(ExploreSetupFailure.DAMAGED, IllegalStateException("bad cid"))

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows loading until the explore session is ready`() = runTest(mainDispatcher) {
        withLoadedPage()
        val navigationStarted = CompletableDeferred<Unit>()
        withFirstAttempt(flow { navigationStarted.await(); emitSuccessAndStay() })

        val viewModel = createViewModel()
        runCurrent()
        assertEquals(LoadingState.Loading, viewModel.state.value)

        navigationStarted.complete(Unit)
        runCurrent()
        assertEquals(LoadingState.Loaded(ExploreProductsContent.Page(webView)), viewModel.state.value)
    }

    @Test
    fun `a host failure shows the retry dialog at once`() = runTest(mainDispatcher) {
        withFirstAttempt(failingWith(hostFailure))

        val viewModel = createViewModel()
        runCurrent()

        assertEquals(LoadingState.Error(hostFailure), viewModel.state.value)
    }

    @Test
    fun `retry after a host failure opens explore again as a retry`() = runTest(mainDispatcher) {
        withFirstAttempt(failingWith(hostFailure))
        withRetryAttempt(flow { awaitCancellation() })
        val viewModel = createViewModel()
        val states = collectStates(viewModel)
        runCurrent()

        viewModel.onRetry()
        runCurrent()

        assertEquals(listOf(LoadingState.Loading, LoadingState.Error(hostFailure), LoadingState.Loading), states)
        verifyAttemptOpened(viewModel, afterRetry = false)
        verifyAttemptOpened(viewModel, afterRetry = true)
    }

    @Test
    fun `skip hides the retry dialog and starts nothing`() = runTest(mainDispatcher) {
        withFirstAttempt(failingWith(hostFailure))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onSkip()
        runCurrent()

        assertEquals(LoadingState.Loaded(ExploreProductsContent.Skipped), viewModel.state.value)
        verifyOneAttemptOpened()
    }

    @Test
    fun `a setup failure waits for the minimum loading time`() {
        listOf(0L, 250L, 499L, 500L, 501L, 700L, 1500L).forEach { arrivalMs ->
            runTest(mainDispatcher) {
                withFirstAttempt(flow { delay(arrivalMs); emit(Result.failure(setupFailure)) })
                val viewModel = createViewModel()
                val shownAt = maxOf(arrivalMs, MIN_LOADING_MS)

                advanceTimeBy(shownAt - 1)
                runCurrent()
                assertEquals("arrival $arrivalMs ms", LoadingState.Loading, viewModel.state.value)

                advanceTimeBy(1)
                runCurrent()
                assertEquals("arrival $arrivalMs ms", LoadingState.Error(setupFailure), viewModel.state.value)
            }
        }
    }

    @Test
    fun `retry after a setup failure can load the page`() = runTest(mainDispatcher) {
        withLoadedPage()
        withFirstAttempt(failingWith(setupFailure))
        val navigationStarted = CompletableDeferred<Unit>()
        withRetryAttempt(flow { navigationStarted.await(); emitSuccessAndStay() })
        val viewModel = createViewModel()
        advanceTimeBy(MIN_LOADING_MS)
        runCurrent()
        assertEquals(LoadingState.Error(setupFailure), viewModel.state.value)

        viewModel.onRetry()
        runCurrent()
        assertEquals(LoadingState.Loading, viewModel.state.value)

        navigationStarted.complete(Unit)
        runCurrent()
        assertEquals(LoadingState.Loaded(ExploreProductsContent.Page(webView)), viewModel.state.value)
    }

    @Test
    fun `a cancelled attempt never shows an error`() = runTest(mainDispatcher) {
        withLoadedPage()
        withFirstAttempt(flow { delay(SLOW_FAILURE_AT_MS); emit(Result.failure(setupFailure)) })
        withRetryAttempt(flow { emitSuccessAndStay() })
        val viewModel = createViewModel()
        val states = collectStates(viewModel)

        advanceTimeBy(QUICK_RETRY_AT_MS)
        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals(listOf(LoadingState.Loading, LoadingState.Loaded(ExploreProductsContent.Page(webView))), states)
    }

    @Test
    fun `two quick retries run one attempt to the end`() = runTest(mainDispatcher) {
        withFirstAttempt(flow { awaitCancellation() })
        withRetryAttempt(flow { awaitCancellation() })
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onRetry()
        viewModel.onRetry()
        runCurrent()

        verifyAttemptOpened(viewModel, afterRetry = true)
    }

    @Test
    fun `pause before load touches nothing`() = runTest(mainDispatcher) {
        withFirstAttempt(flow { awaitCancellation() })
        val viewModel = createViewModel()
        runCurrent()

        viewModel.pauseConnections()

        verifyConnectionsNotPaused()
    }

    @Test
    fun `pause after load pauses the loaded page`() = runTest(mainDispatcher) {
        withLoadedPage()
        withFirstAttempt(flow { emitSuccessAndStay() })
        val viewModel = createViewModel()
        runCurrent()

        viewModel.pauseConnections()

        verifyConnectionsPaused()
    }

    @Test
    fun `a setup failure after retry waits for the minimum loading time again`() = runTest(mainDispatcher) {
        withFirstAttempt(failingWith(setupFailure))
        withRetryAttempt(failingWith(setupFailure))
        val viewModel = createViewModel()
        advanceTimeBy(RETRY_AT_MS)
        runCurrent()
        assertEquals(LoadingState.Error(setupFailure), viewModel.state.value)

        viewModel.onRetry()
        advanceTimeBy(MIN_LOADING_MS - 1)
        runCurrent()
        assertEquals(LoadingState.Loading, viewModel.state.value)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(LoadingState.Error(setupFailure), viewModel.state.value)
    }

    private fun createViewModel() = ExploreProductsViewModel(interactor = interactor, router = router)

    private fun TestScope.collectStates(viewModel: ExploreProductsViewModel): List<LoadingState<ExploreProductsContent>> {
        val states = mutableListOf<LoadingState<ExploreProductsContent>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.toList(states) }
        return states
    }

    private suspend fun withLoadedPage() {
        whenever(provider.getWebView()).thenReturn(webView)
    }

    private fun withFirstAttempt(attempt: Flow<Result<BrowserWebViewProvider>>) {
        whenever(with(any<StalenessReportCollector>()) { interactor.openExplore(eq(false), any()) }).thenReturn(attempt)
    }

    private fun withRetryAttempt(attempt: Flow<Result<BrowserWebViewProvider>>) {
        whenever(with(any<StalenessReportCollector>()) { interactor.openExplore(eq(true), any()) }).thenReturn(attempt)
    }

    private fun failingWith(error: ExploreLoadError): Flow<Result<BrowserWebViewProvider>> = flow { emit(Result.failure(error)) }

    private suspend fun FlowCollector<Result<BrowserWebViewProvider>>.emitSuccessAndStay() {
        emit(Result.success(provider))
        awaitCancellation()
    }

    private fun verifyAttemptOpened(viewModel: ExploreProductsViewModel, afterRetry: Boolean) {
        val verified = verify(interactor)
        with(eq<StalenessReportCollector>(viewModel.stalenessReport)) { verified.openExplore(eq(afterRetry), any()) }
    }

    private fun verifyOneAttemptOpened() {
        val verified = verify(interactor, times(1))
        with(any<StalenessReportCollector>()) { verified.openExplore(anyBoolean(), any()) }
    }

    private fun verifyConnectionsPaused() {
        verify(provider).pauseConnections()
    }

    private fun verifyConnectionsNotPaused() {
        verify(provider, never()).pauseConnections()
    }

    private companion object {
        const val MIN_LOADING_MS = 500L
        const val RETRY_AT_MS = 700L
        const val SLOW_FAILURE_AT_MS = 1_000L
        const val QUICK_RETRY_AT_MS = 100L
    }
}
