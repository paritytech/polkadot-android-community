package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

import android.net.Uri
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.childScope
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.mapError
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.progressStallReport.markRegion
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.ResolvedProduct
import io.paritytech.polkadotapp.feature_products_api.model.toUrl
import io.paritytech.polkadotapp.feature_products_impl.domain.browser.ExploreSession
import io.paritytech.polkadotapp.feature_products_impl.domain.browser.ExploreSessionFactory
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiSession
import io.paritytech.polkadotapp.feature_products_impl.domain.usecase.ResolveProductUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.appServingHost
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import io.paritytech.polkadotapp.common.R as RCommon

class ExploreProductsInteractor @Inject constructor(
    private val exploreProductsService: ExploreProductsService,
    private val exploreSessionFactory: ExploreSessionFactory,
    private val resolveProductUseCase: ResolveProductUseCase,
    private val dotNsResolver: DotNsResolver,
    private val remoteConfigService: RemoteConfigService,
    private val dispatchers: CoroutineDispatchers,
) {
    context(diagnostics: StalenessReportCollector)
    fun openExplore(afterRetry: Boolean, onProductSelected: (ProductId) -> Unit): Flow<Result<BrowserWebViewProvider>> = flow {
        coroutineScope {
            val attemptScope = childScope()
            try {
                val result = open(attemptScope, afterRetry, onProductSelected)
                emit(result)
                if (result.isSuccess) awaitCancellation()
            } finally {
                attemptScope.cancel()
            }
        }
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun open(
        scope: CoroutineScope,
        afterRetry: Boolean,
        onProductSelected: (ProductId) -> Unit,
    ): Result<BrowserWebViewProvider> = diagnostics.markRegion(RCommon.string.explore_products_stall_opening) {
        exploreProductsService.getExploreProductId()
            .logFailure("Failed to resolve explore url")
            .flatMap { productId ->
                runCatching { exploreSessionFactory.create(productId.toUrl(), scope, onProductSelected) }
                    .map { session -> productId to session }
            }
            .mapError { ExploreLoadError.HostUnresolved(it) }
            .flatMap { (productId, session) -> setUp(productId, session, afterRetry) }
    }

    private suspend fun setUp(productId: ProductId, session: ExploreSession, afterRetry: Boolean): Result<BrowserWebViewProvider> {
        return syncRemoteConfig()
            .flatMap { resolveProduct(productId) }
            .flatMap { resolved -> loadContent(resolved.appServingHost(fallback = productId.value), afterRetry) }
            .flatMap { initialize(session.hostApiSession) }
            .map { session.provider }
    }

    private suspend fun syncRemoteConfig(): Result<Unit> {
        val synced = withTimeoutOrNull(REMOTE_CONFIG_SYNC_LIMIT) { remoteConfigService.sync() }
            ?: Result.failure(TimeoutException("No remote config sync within $REMOTE_CONFIG_SYNC_LIMIT"))

        return synced.mapError { ExploreLoadError.SetupFailed(ExploreSetupFailure.UNREACHABLE, it) }
    }

    private suspend fun resolveProduct(productId: ProductId): Result<ResolvedProduct> {
        return withContext(dispatchers.computation) { resolveProductUseCase.resolve(productId) }
            .mapError { ExploreLoadError.SetupFailed(classifyProductFailure(it), it) }
    }

    private suspend fun loadContent(servingHost: String, afterRetry: Boolean): Result<Uri> {
        if (afterRetry) dotNsResolver.invalidate(servingHost)

        return dotNsResolver.resolveToLocalUri(servingHost)
            .mapError { ExploreLoadError.SetupFailed(classifyContentFailure(it), it) }
    }

    private suspend fun initialize(hostApiSession: HostApiSession): Result<Unit> {
        return runCancellableCatching { hostApiSession.initialize() }
            .mapError { ExploreLoadError.SetupFailed(ExploreSetupFailure.UNKNOWN, it) }
    }

    private companion object {
        val REMOTE_CONFIG_SYNC_LIMIT = 30.seconds
    }
}
