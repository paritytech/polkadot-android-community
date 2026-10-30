package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

import android.net.Uri
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
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
import io.paritytech.polkadotapp.test_shared.thenSuspend
import io.paritytech.polkadotapp.test_shared.thenThrowUnsafe
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ExploreProductsInteractorTest {
    private val exploreProductsService: ExploreProductsService = mock()
    private val exploreSessionFactory: ExploreSessionFactory = mock()
    private val resolveProductUseCase: ResolveProductUseCase = mock()
    private val dotNsResolver: DotNsResolver = mock()
    private val remoteConfigService: RemoteConfigService = mock()

    private val provider: BrowserWebViewProvider = mock()
    private val hostApiSession: HostApiSession = mock()
    private lateinit var sessionScope: CoroutineScope

    private val interactor = ExploreProductsInteractor(
        exploreProductsService = exploreProductsService,
        exploreSessionFactory = exploreSessionFactory,
        resolveProductUseCase = resolveProductUseCase,
        dotNsResolver = dotNsResolver,
        remoteConfigService = remoteConfigService,
        dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    @Test
    fun `a host failure is HostUnresolved and builds no session`() = runBlocking<Unit> {
        withNoExploreHost()

        val result = open(afterRetry = false)

        assertFailureOf<ExploreLoadError.HostUnresolved>(result)
        verifySessionNeverBuilt()
    }

    @Test
    fun `a remote config sync failure is SetupFailed and the product is never resolved`() = runBlocking<Unit> {
        withExploreHost()
        withSession()
        withRemoteConfigSyncFailing()

        val result = open(afterRetry = false)

        assertFailureOf<ExploreLoadError.SetupFailed>(result)
        verifyProductNeverResolved()
    }

    @Test
    fun `a content failure is SetupFailed`() = runBlocking<Unit> {
        withExploreHost()
        withSession()
        withRemoteConfigSynced()
        withProduct()
        withContentFailing()

        val result = open(afterRetry = false)

        assertFailureOf<ExploreLoadError.SetupFailed>(result)
    }

    @Test
    fun `content resolution that throws is SetupFailed`() = runBlocking<Unit> {
        withExploreHost()
        withSession()
        withRemoteConfigSynced()
        withProduct()
        withContentThrowing()

        val result = open(afterRetry = false)

        assertFailureOf<ExploreLoadError.SetupFailed>(result)
    }

    @Test
    fun `an initialize failure is SetupFailed`() = runBlocking<Unit> {
        withExploreHost()
        withSession()
        withRemoteConfigSynced()
        withProduct()
        withContent()
        withInitializeFailing()

        val result = open(afterRetry = false)

        assertFailureOf<ExploreLoadError.SetupFailed>(result)
    }

    @Test
    fun `emits the page provider when every step succeeds`() = runBlocking<Unit> {
        withExploreHost()
        withSession()
        withRemoteConfigSynced()
        withProduct()
        withContent()
        withInitialized()

        val result = open(afterRetry = false)

        assertSame(provider, result.getOrNull())
    }

    @Test
    fun `a successful attempt keeps its session scope alive until collection stops`() = runBlocking<Unit> {
        withExploreHost()
        withSession()
        withRemoteConfigSynced()
        withProduct()
        withContent()
        withInitialized()
        val opened = CompletableDeferred<Unit>()

        val collecting = launch { attempt(afterRetry = false).collect { opened.complete(Unit) } }
        opened.await()
        assertTrue(sessionScope.isActive)

        collecting.cancelAndJoin()
        assertFalse(sessionScope.isActive)
    }

    @Test
    fun `a retry whose remote config sync never answers fails at 30 seconds and not before`() = runTest {
        withExploreHost()
        withSession()
        withRemoteConfigSyncNeverAnswering()

        val result = async { open(afterRetry = true) }
        advanceTimeBy(REMOTE_CONFIG_SYNC_LIMIT - 1.milliseconds)
        assertFalse(result.isCompleted)
        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertTrue(result.isCompleted)

        assertFailureOf<ExploreLoadError.SetupFailed>(result.await())
    }

    @Test
    fun `a retry invalidates the content before resolving it`() = runBlocking<Unit> {
        withExploreHost()
        withSession()
        withRemoteConfigSynced()
        withProduct()
        withContent()
        withInitialized()

        open(afterRetry = true)

        verifyContentInvalidatedBeforeResolving()
    }

    @Test
    fun `a first attempt does not invalidate the content`() = runBlocking<Unit> {
        withExploreHost()
        withSession()
        withRemoteConfigSynced()
        withProduct()
        withContent()
        withInitialized()

        open(afterRetry = false)

        verifyContentNeverInvalidated()
    }

    private suspend fun open(afterRetry: Boolean): Result<BrowserWebViewProvider> {
        return attempt(afterRetry).first()
    }

    private fun attempt(afterRetry: Boolean): Flow<Result<BrowserWebViewProvider>> {
        return with(StalenessReportCollector.NoOp) { interactor.openExplore(afterRetry, onProductSelected = {}) }
    }

    private suspend fun withExploreHost() {
        whenever(exploreProductsService.getExploreProductId()).thenReturn(Result.success(PRODUCT_ID))
    }

    private suspend fun withNoExploreHost() {
        whenever(exploreProductsService.getExploreProductId()).thenReturn(Result.failure(IllegalStateException("no tld")))
    }

    private fun withSession() {
        whenever(exploreSessionFactory.create(any(), any(), any())).thenAnswer { call ->
            sessionScope = call.getArgument(1)
            ExploreSession(provider, hostApiSession)
        }
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

    private suspend fun withProduct() {
        whenever(resolveProductUseCase.resolve(PRODUCT_ID)).thenReturn(Result.success(resolvedProduct()))
    }

    private suspend fun withContent() {
        whenever(dotNsResolver.resolveToLocalUri(APP_HOST)).thenReturn(Result.success(mock<Uri>()))
    }

    private suspend fun withContentFailing() {
        whenever(dotNsResolver.resolveToLocalUri(APP_HOST)).thenReturn(Result.failure(IllegalStateException("no content")))
    }

    private suspend fun withContentThrowing() {
        whenever(dotNsResolver.resolveToLocalUri(APP_HOST)).thenThrowUnsafe(IllegalArgumentException("Unsupported EIP-1577 prefix"))
    }

    private suspend fun withInitialized() {
        whenever(hostApiSession.initialize()).thenReturn(Unit)
    }

    private suspend fun withInitializeFailing() {
        whenever(hostApiSession.initialize()).thenThrowUnsafe(IllegalStateException("bridge script missing"))
    }

    private fun verifySessionNeverBuilt() {
        verifyNoInteractions(exploreSessionFactory)
    }

    private fun verifyProductNeverResolved() {
        verifyNoInteractions(resolveProductUseCase)
    }

    private suspend fun verifyContentInvalidatedBeforeResolving() {
        val order = inOrder(dotNsResolver)
        order.verify(dotNsResolver).invalidate(APP_HOST)
        order.verify(dotNsResolver).resolveToLocalUri(APP_HOST)
    }

    private suspend fun verifyContentNeverInvalidated() {
        verify(dotNsResolver, never()).invalidate(any())
    }

    private fun resolvedProduct() = ResolvedProduct(
        product = Product(id = PRODUCT_ID, name = "Browse", icon = null),
        executables = Executables(
            app = ProductExecutable.App(host = ExecutableHost(APP_HOST), appVersion = SemVer.ZERO),
            widget = null,
            worker = null,
        ),
    )

    private companion object {
        const val APP_HOST = "app.browse.paseo"
        val PRODUCT_ID: ProductId = ProductId.fromStoredValue("browse.paseo")
        val REMOTE_CONFIG_SYNC_LIMIT = 30.seconds
    }
}
