package io.paritytech.polkadotapp.feature_products_impl.domain.webView

import android.graphics.Bitmap
import android.webkit.WebBackForwardList
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import io.paritytech.polkadotapp.common.utils.notFoundResponse
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.CallingProductIdProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.getProductIdOrNull
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.ProductPermissionGuard
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.handlers.NetworkAccessPermissionHandler
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

class WebViewPermissionClientFactory @Inject constructor(
    private val permissionGuard: ProductPermissionGuard
) {
    fun create(callingProductIdProvider: CallingProductIdProvider): WebViewPermissionClient {
        return WebViewPermissionClient(callingProductIdProvider, permissionGuard)
    }
}

class WebViewPermissionClient(
    private val productIdProvider: CallingProductIdProvider,
    private val permissionGuard: ProductPermissionGuard
) : WebViewClient() {
    /**
     * Last two entries from back-forward history, captured on [onPageStarted].
     *
     * Allows stale sub-resources (e.g. favicon) from the previous site during navigation, since
     * [shouldInterceptRequest] can fire for them before [onPageStarted] is invoked for the new page.
     */
    @Volatile
    private var recentProductIds: List<ProductId> = emptyList()

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        val history = view.copyBackForwardList()
        val current = history.currentIndex
        recentProductIds = listOfNotNull(
            history.productIdAt(current),
            history.productIdAt(current - 1),
        )
    }

    private fun WebBackForwardList.productIdAt(index: Int): ProductId? {
        if (index < 0 || index >= size) return null
        val url = getItemAtIndex(index)?.url ?: return null
        return ProductId.fromUrl(url.toUri()).getOrNull()
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return super.shouldInterceptRequest(view, request)
        if (url.scheme == "data") return super.shouldInterceptRequest(view, request)
        if (request.isForMainFrame) return super.shouldInterceptRequest(view, request)

        val callingProductId = runBlocking { productIdProvider.getProductIdOrNull() }

        if (callingProductId == null) {
            Timber.e("WebView Permissions: Blocked outbound request since calling product is null")
            return notFoundResponse()
        }

        val requestProductId = ProductId.fromUrl(url).getOrNull()
        if (requestProductId == callingProductId || requestProductId in recentProductIds) {
            return super.shouldInterceptRequest(view, request)
        }

        val domain = NetworkAccessPermissionHandler.extractDomain(url.toString())
        if (domain != null) {
            val granted = runBlocking {
                permissionGuard.consumePermission(callingProductId, ProductPermission.RemotePermission.NetworkAccess(domain))
            }
            if (granted) {
                return super.shouldInterceptRequest(view, request)
            }
        }

        Timber.w("Blocked outbound request from product $callingProductId: $url")
        return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), null)
    }
}
