package io.paritytech.polkadotapp.feature_products_impl.domain.webView

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import io.paritytech.polkadotapp.common.utils.LocalDevHost
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsNavigationType
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsUtils
import io.paritytech.polkadotapp.feature_dotns_api.presentation.DotNsServingHostResolver
import io.paritytech.polkadotapp.feature_dotns_api.presentation.DotNsWebViewClient
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationResult

/**
 * WebViewClient for browser environments (SPA + Explore).
 *
 * Serves dotNS domains from local storage via [DotNsWebViewClient].
 * Delegates navigation decisions to [NavigationPolicy], passing a pre-classified [DotNsNavigationType].
 */
class BrowserWebViewClient(
    dotNsResolver: DotNsResolver,
    private val dotNsTldProvider: DotNsTldProvider,
    servingHostResolver: DotNsServingHostResolver,
    private val navigationPolicy: NavigationPolicy,
    mainDocumentResponseHeaders: Map<String, String>,
    private val localDevOrigin: String?,
) : DotNsWebViewClient(dotNsResolver, dotNsTldProvider, servingHostResolver, mainDocumentResponseHeaders) {
    /**
     * A request to the dev origin is never a dotNS one, so it is answered before [DotNsWebViewClient]
     * blocks this thread waiting for the active network's TLD — which a dev product never needs, and
     * which never arrives when the chain config is unavailable.
     */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (localDevOrigin != null && request.url.isOnLocalDevOrigin()) return null

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val origin = view.url?.toUri()
        val destination = request.url
        val tld = dotNsTldProvider.currentTldOrNull()
        val type = DotNsUtils.classifyNavigation(origin, destination, tld, localDevOrigin)
        val result = navigationPolicy.handleNavigation(type, destination)

        return when (result) {
            NavigationResult.INTERCEPTED_BY_POLICY -> true
            NavigationResult.DELEGATE_TO_WEBVIEW -> false
        }
    }

    private fun Uri.isOnLocalDevOrigin(): Boolean = LocalDevHost.parseOrigin(toString()) == localDevOrigin
}
