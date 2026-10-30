package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

import android.net.Uri
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.childScope
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.mapError
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.progressStallReport.markRegion
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.toUrl
import io.paritytech.polkadotapp.feature_products_impl.domain.browser.ExploreSession
import io.paritytech.polkadotapp.feature_products_impl.domain.browser.ExploreSessionFactory
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
            .flatMap { productId ->
                runCatching { exploreSessionFactory.create(productId.toUrl(), scope, onProductSelected) }
                    .map { session -> productId to session }
            }
            .mapError { ExploreLoadError.HostUnresolved(it) }
            .flatMap { (productId, session) -> setUp(productId, session, afterRetry) }
    }

    private suspend fun setUp(productId: ProductId, session: ExploreSession, afterRetry: Boolean): Result<BrowserWebViewProvider> {
        return syncRemoteConfig()
            .flatMap { withContext(dispatchers.computation) { resolveProductUseCase.resolve(productId) } }
            .flatMap { resolved -> loadContent(resolved.appServingHost(fallback = productId.value), afterRetry) }
            .flatMap { runCancellableCatching { session.hostApiSession.initialize() } }
            .map { session.provider }
            .mapError { ExploreLoadError.SetupFailed(it) }
    }

    private suspend fun syncRemoteConfig(): Result<Unit> {
        return withTimeoutOrNull(REMOTE_CONFIG_SYNC_LIMIT) { remoteConfigService.sync() }
            ?: Result.failure(TimeoutException("No remote config sync within $REMOTE_CONFIG_SYNC_LIMIT"))
    }

    private suspend fun loadContent(servingHost: String, afterRetry: Boolean): Result<Uri> {
        if (afterRetry) dotNsResolver.invalidate(servingHost)

        return runCancellableCatching { dotNsResolver.resolveToLocalUri(servingHost) }.flatMap { it }
    }

    private companion object {
        val REMOTE_CONFIG_SYNC_LIMIT = 30.seconds
    }
}
