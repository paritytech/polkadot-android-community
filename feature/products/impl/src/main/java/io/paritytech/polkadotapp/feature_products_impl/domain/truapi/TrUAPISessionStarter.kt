package io.paritytech.polkadotapp.feature_products_impl.domain.truapi

import android.net.Uri
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Boots a [ProductTrUAPIHostBridge] for a product WebView: opens its execution,
 * registers the bootstrap at document start, and only then triggers
 * the initial page load — the bootstrap must be in place before the page loads
 * or the product never connects.
 */
class TrUAPISessionStarter @Inject constructor(
    private val hostBridgeFactory: ProductTrUAPIHostBridge.Factory,
    private val runtimeProvider: TrUAPIHostRuntimeProvider,
    private val chainDirectory: TrUAPIChainDirectory,
    private val dotNsTldProvider: DotNsTldProvider,
) {
    fun start(
        provider: BrowserWebViewProvider,
        productUrl: String,
        scope: CoroutineScope,
        hostApiNavigation: NavigationPolicy,
    ): ProductTrUAPIHostBridge {
        val bridge = hostBridgeFactory.create(scope)
        scope.launch {
            attachAndLoad(bridge, provider, productUrl, hostApiNavigation)
                .logFailure("Failed to start TrUAPI host bridge for $productUrl")
        }
        return bridge
    }

    private suspend fun attachAndLoad(
        bridge: ProductTrUAPIHostBridge,
        provider: BrowserWebViewProvider,
        productUrl: String,
        navigation: NavigationPolicy,
    ): Result<Unit> {
        val sharedRuntime = runtimeProvider.runtime()
        return dotNsTldProvider.getTld()
            .flatMap { tld -> ProductId.fromUrl(productUrl.toUri(), tld) }
            .flatMap { productId -> sharedRuntime.map { it to productId } }
            .mapCatching { (runtime, productId) ->
                // Before attach(), which starts the loopback bridge and sets the
                // execution before invoking the callback: failing in there would
                // leave a live listener that the guard then blocks re-attaching.
                check(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    "WebView lacks DOCUMENT_START_SCRIPT; cannot run a TrUAPI product"
                }

                val chains = chainDirectory.resolve()
                val webView = provider.getWebView()
                // The bootstrap publishes the loopback port and its bearer token, so it
                // goes to the product's own origin only. A wildcard would hand the
                // bridge endpoint to any page the WebView is ever pointed at.
                val origins = setOf(productUrl.toUri().origin())

                bridge.attach(runtime, productId, chains, navigation) { bootstrap ->
                    WebViewCompat.addDocumentStartJavaScript(webView, bootstrap, origins)
                }

                provider.loadInitialContent()
            }
    }

    private fun Uri.origin(): String = buildString {
        append(scheme).append("://").append(host)
        port.takeIf { it != -1 }?.let { append(':').append(it) }
    }
}
