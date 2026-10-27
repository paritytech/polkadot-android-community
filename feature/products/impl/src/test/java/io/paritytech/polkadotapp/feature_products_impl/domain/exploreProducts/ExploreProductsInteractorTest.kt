package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

import android.net.Uri
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessRegionHandle
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolutionError
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_products_api.domain.error.ProductResolutionError
import io.paritytech.polkadotapp.feature_products_api.model.ExecutableHost
import io.paritytech.polkadotapp.feature_products_api.model.Executables
import io.paritytech.polkadotapp.feature_products_api.model.Product
import io.paritytech.polkadotapp.feature_products_api.model.ProductExecutable
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.ResolvedProduct
import io.paritytech.polkadotapp.feature_products_api.model.SemVer
import io.paritytech.polkadotapp.feature_products_impl.domain.browser.ExploreSession
import io.paritytech.polkadotapp.feature_products_impl.domain.browser.ExploreSessionFactory
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiSession
import io.paritytech.polkadotapp.feature_products_impl.domain.usecase.ResolveProductUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import io.paritytech.polkadotapp.test_shared.TestCoroutineDispatchers
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.assertFailureOf
import io.paritytech.polkadotapp.test_shared.settle
import io.paritytech.polkadotapp.test_shared.thenSuspend
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreProductsInteractorTest {
    private val exploreProductsService: ExploreProductsService = mock()
    private val sessionFactory: ExploreSessionFactory = mock()
    private val resolveProductUseCase: ResolveProductUseCase = mock()
    private val dotNsResolver: DotNsResolver = mock()
    private val remoteConfigService: RemoteConfigService = mock()

    private val provider: BrowserWebViewProvider = mock()
    private val hostApiSession: HostApiSession = mock()

    private var sessionScope: CoroutineScope? = null

    private val interactor = ExploreProductsInteractor(
        exploreProductsService = exploreProductsService,
        exploreSessionFactory = sessionFactory,
        resolveProductUseCase = resolveProductUseCase,
        dotNsResolver = dotNsResolver,
        remoteConfigService = remoteConfigService,
        dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    @Test
    fun `emits nothing until the page has started loading`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()
        val navigationStarted = CompletableDeferred<Unit>()
        withInitializeWaitingFor(navigationStarted)

        val results = collectAttempt(afterRetry = false) {
            assertTrue(it.isEmpty())
            navigationStarted.complete(Unit)
        }

        assertSame(provider, results.single().getOrNull())
    }

    @Test
    fun `a host failure starts no setup`() = runBlocking<Unit> {
        withNoExploreHost()

        val results = collectAttempt(afterRetry = false)

        assertFailureOf<ExploreLoadError.HostUnresolved>(results.single())
        verifyNoSetupStarted()
    }

    @Test
    fun `a failed session assembly is a host failure`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssemblyFailing()

        val results = collectAttempt(afterRetry = false)

        assertFailureOf<ExploreLoadError.HostUnresolved>(results.single())
    }

    @Test
    fun `resolves the content of the host the page is served from`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = APP_HOST)
        withContentResolved()

        collectAttempt(afterRetry = false)

        verifyContentResolvedFor(APP_HOST)
    }

    @Test
    fun `a product failure is a setup failure`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductFailing(ProductResolutionError.MalformedManifest)

        val results = collectAttempt(afterRetry = false)

        assertSetupFailed(ExploreSetupFailure.DAMAGED, results.single())
        verifyNotInitialized()
    }

    @Test
    fun `a content failure is a setup failure`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentFailing(DotNsResolutionError.GatewayRejected(404))

        val results = collectAttempt(afterRetry = false)

        assertSetupFailed(ExploreSetupFailure.UNKNOWN, results.single())
        verifyNotInitialized()
    }

    @Test
    fun `an initialize failure is a setup failure`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()
        withInitializeFailing()

        val results = collectAttempt(afterRetry = false)

        assertSetupFailed(ExploreSetupFailure.UNKNOWN, results.single())
    }

    @Test
    fun `a retry clears the cached content before resolving it`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()

        collectAttempt(afterRetry = true)

        verifyContentInvalidatedBeforeResolving(PRODUCT_HOST)
    }

    @Test
    fun `a first attempt never clears the cache`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()

        collectAttempt(afterRetry = false)

        verifyContentNeverInvalidated()
    }

    @Test
    fun `cancelling an attempt cancels its session scope`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()

        collectAttempt(afterRetry = false) {
            assertTrue(sessionScope!!.isActive)
        }

        assertFalse(sessionScope!!.isActive)
    }

    @Test
    fun `a failed attempt cancels its session scope`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()
        withInitializeFailing()

        val results = collectAttempt(afterRetry = false) {
            assertFalse(sessionScope!!.isActive)
        }

        assertSetupFailed(ExploreSetupFailure.UNKNOWN, results.single())
    }

    @Test
    fun `opening reports one stall region that ends once the page has started loading`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()
        val (diagnostics, region) = recordingDiagnostics()

        collectAttempt(afterRetry = false, diagnostics = diagnostics) {
            verifyOneRegionEnded(diagnostics, region)
        }
    }

    @Test
    fun `a remote config sync that fails is a setup failure`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSyncFailing()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()

        val results = collectAttempt(afterRetry = true)

        assertSetupFailed(ExploreSetupFailure.UNREACHABLE, results.single())
        verifyProductNeverResolved()
    }

    @Test
    fun `a remote config sync that never answers fails at the limit`() = runTest {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSyncNeverAnswering()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()

        val results = startAttempt(afterRetry = false)

        advanceTimeBy(SYNC_LIMIT_MS - 1)
        runCurrent()
        assertTrue("nothing emitted before the limit", results.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals("one result at the limit", 1, results.size)
        assertSetupFailed(ExploreSetupFailure.UNREACHABLE, results.single())
    }

    @Test
    fun `every attempt syncs remote config before resolving the product`() = runBlocking<Unit> {
        withExploreHost()
        withSessionAssembled()
        withRemoteConfigSynced()
        withProductResolved(appHost = PRODUCT_HOST)
        withContentResolved()

        collectAttempt(afterRetry = false)
        collectAttempt(afterRetry = true)

        verifyRemoteConfigSyncedBeforeEachProductResolve(attempts = 2)
    }

    private fun TestScope.startAttempt(afterRetry: Boolean): List<Result<BrowserWebViewProvider>> {
        val results = mutableListOf<Result<BrowserWebViewProvider>>()
        backgroundScope.launch { openAttempt(afterRetry, StalenessReportCollector.NoOp).collect { results += it } }
        return results
    }

    private suspend fun collectAttempt(
        afterRetry: Boolean,
        diagnostics: StalenessReportCollector = StalenessReportCollector.NoOp,
        whileCollecting: suspend (List<Result<BrowserWebViewProvider>>) -> Unit = {},
    ): List<Result<BrowserWebViewProvider>> = coroutineScope {
        val results = mutableListOf<Result<BrowserWebViewProvider>>()
        val collection: Job = launch { openAttempt(afterRetry, diagnostics).collect { results += it } }
        settle()
        whileCollecting(results)
        settle()
        collection.cancel()
        collection.join()
        results
    }

    private fun openAttempt(afterRetry: Boolean, diagnostics: StalenessReportCollector) =
        with(diagnostics) { interactor.openExplore(afterRetry, onProductSelected = {}) }

    private suspend fun withExploreHost() {
        whenever(exploreProductsService.getExploreProductId()).thenReturn(Result.success(PRODUCT_ID))
    }

    private suspend fun withNoExploreHost() {
        whenever(exploreProductsService.getExploreProductId()).thenReturn(Result.failure(IllegalStateException("no tld")))
    }

    private fun withSessionAssembled() {
        whenever(sessionFactory.create(any(), any(), any())).thenAnswer { invocation ->
            sessionScope = invocation.getArgument(1)
            ExploreSession(provider, hostApiSession)
        }
    }

    private fun withSessionAssemblyFailing() {
        whenever(sessionFactory.create(any(), any(), any())).thenThrow(IllegalStateException("no webview"))
    }

    private suspend fun withRemoteConfigSynced() {
        whenever(remoteConfigService.sync()).thenReturn(Result.success(Unit))
    }

    private suspend fun withRemoteConfigSyncFailing() {
        whenever(remoteConfigService.sync()).thenReturn(Result.failure(IllegalStateException("offline")))
    }

    private suspend fun withRemoteConfigSyncNeverAnswering() {
        whenever(remoteConfigService.sync()).thenSuspend { awaitCancellation() }
    }

    private suspend fun withProductResolved(appHost: String) {
        val resolved = ResolvedProduct(
            product = Product(id = PRODUCT_ID, name = "Browse", icon = null),
            executables = Executables(
                app = ProductExecutable.App(host = ExecutableHost(appHost), appVersion = SemVer.ZERO),
                widget = null,
                worker = null,
            ),
        )
        whenever(resolveProductUseCase.resolve(PRODUCT_ID)).thenReturn(Result.success(resolved))
    }

    private suspend fun withProductFailing(error: Throwable) {
        whenever(resolveProductUseCase.resolve(PRODUCT_ID)).thenReturn(Result.failure(error))
    }

    private suspend fun withContentResolved() {
        whenever(dotNsResolver.resolveToLocalUri(any())).thenReturn(Result.success(mock<Uri>()))
    }

    private suspend fun withContentFailing(error: Throwable) {
        whenever(dotNsResolver.resolveToLocalUri(any())).thenReturn(Result.failure(error))
    }

    private suspend fun withInitializeWaitingFor(navigationStarted: CompletableDeferred<Unit>) {
        whenever(hostApiSession.initialize()).thenSuspend { navigationStarted.await() }
    }

    private suspend fun withInitializeFailing() {
        whenever(hostApiSession.initialize()).thenThrow(IllegalStateException("bridge script missing"))
    }

    private fun recordingDiagnostics(): Pair<StalenessReportCollector, StalenessRegionHandle> {
        val diagnostics: StalenessReportCollector = mock()
        val region: StalenessRegionHandle = mock()
        whenever(diagnostics.startRegion(any())).thenReturn(region)
        return diagnostics to region
    }

    private fun verifyOneRegionEnded(diagnostics: StalenessReportCollector, region: StalenessRegionHandle) {
        verify(diagnostics).startRegion(any())
        verify(region).end()
    }

    private suspend fun verifyRemoteConfigSyncedBeforeEachProductResolve(attempts: Int) {
        val order = inOrder(remoteConfigService, resolveProductUseCase)
        repeat(attempts) {
            order.verify(remoteConfigService).sync()
            order.verify(resolveProductUseCase).resolve(PRODUCT_ID)
        }
    }

    private fun verifyNoSetupStarted() {
        verifyNoInteractions(sessionFactory, remoteConfigService, resolveProductUseCase, dotNsResolver)
    }

    private fun verifyProductNeverResolved() {
        verifyNoInteractions(resolveProductUseCase)
    }

    private suspend fun verifyContentResolvedFor(host: String) {
        verify(dotNsResolver).resolveToLocalUri(host)
    }

    private suspend fun verifyContentInvalidatedBeforeResolving(host: String) {
        val order = inOrder(dotNsResolver)
        order.verify(dotNsResolver).invalidate(host)
        order.verify(dotNsResolver).resolveToLocalUri(host)
    }

    private suspend fun verifyContentNeverInvalidated() {
        verify(dotNsResolver, never()).invalidate(any())
    }

    private suspend fun verifyNotInitialized() {
        verify(hostApiSession, never()).initialize()
    }

    private fun assertSetupFailed(expected: ExploreSetupFailure, result: Result<*>) {
        assertFailureOf<ExploreLoadError.SetupFailed>(result)
        assertEquals(expected, (result.exceptionOrNull() as ExploreLoadError.SetupFailed).kind)
    }

    private companion object {
        const val PRODUCT_HOST = "browse.paseo"
        const val APP_HOST = "app.browse.paseo"
        const val SYNC_LIMIT_MS = 30_000L
        val PRODUCT_ID: ProductId = ProductId.fromStoredValue(PRODUCT_HOST)
    }
}
