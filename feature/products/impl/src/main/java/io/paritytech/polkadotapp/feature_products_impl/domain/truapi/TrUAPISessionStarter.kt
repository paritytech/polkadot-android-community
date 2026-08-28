package io.paritytech.polkadotapp.feature_products_impl.domain.truapi

import android.net.Uri
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.parity.truapi.RuntimeConfig
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
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
 * Boots a [ProductTrUAPIHostBridge] for a product WebView: builds the runtime
 * config, registers the bootstrap at document start, and only then triggers
 * the initial page load — the bootstrap must be in place before the page loads
 * or the product never connects.
 */
class TrUAPISessionStarter @Inject constructor(
    private val hostBridgeFactory: ProductTrUAPIHostBridge.Factory,
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
    private val chainDirectory: TrUAPIChainDirectory,
    private val localSessionSource: TrUAPILocalSessionSource,
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
    ): Result<Unit> = dotNsTldProvider.getTld()
        .flatMap { tld -> ProductId.fromUrl(productUrl.toUri(), tld) }
        .mapCatching { productId ->
            // Before attach(), which starts the loopback bridge and sets `core`
            // before invoking the callback: failing in there would leave a live
            // listener that the `core != null` guard then blocks re-attaching.
            check(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                "WebView lacks DOCUMENT_START_SCRIPT; cannot run a TrUAPI product"
            }

            val config = buildRuntimeConfig(productId)
            val chains = chainDirectory.resolve()
            val webView = provider.getWebView()
            // The bootstrap publishes the loopback port and its bearer token, so it
            // goes to the product's own origin only. A wildcard would hand the
            // bridge endpoint to any page the WebView is ever pointed at.
            val origins = setOf(productUrl.toUri().origin())

            bridge.attach(config, chains, navigation) { bootstrap ->
                WebViewCompat.addDocumentStartJavaScript(webView, bootstrap, origins)
            }

            provider.loadInitialContent()
        }

    private fun Uri.origin(): String = buildString {
        append(scheme).append("://").append(host)
        port.takeIf { it != -1 }?.let { append(':').append(it) }
    }

    private suspend fun buildRuntimeConfig(productId: ProductId): RuntimeConfig {
        val peopleGenesis = chainRegistry.getChain(knownChains.people).genesisHash.value
        val bulletinGenesis = chainRegistry.getChain(knownChains.bulletIn).genesisHash.value
        // Booting without a session is the pre-session behaviour: the product
        // loads and every signing call fails. Worth degrading to rather than
        // refusing the product outright.
        val localSession = localSessionSource.resolve()
            .logFailure("TrUAPI local session unavailable; running $productId without one")
            .getOrNull()

        return RuntimeConfig(
            productId = productId.value,
            hostName = HOST_NAME,
            peopleChainGenesisHash = peopleGenesis,
            bulletinChainGenesisHash = bulletinGenesis,
            localSessionSecret = localSession?.secret,
            localSessionLiteUsername = localSession?.liteUsername,
        )
    }

    private companion object {
        const val HOST_NAME = "Polkadot"
    }
}
