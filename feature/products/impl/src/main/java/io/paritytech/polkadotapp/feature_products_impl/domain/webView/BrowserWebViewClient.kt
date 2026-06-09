package io.paritytech.polkadotapp.feature_products_impl.domain.webView

import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.net.toUri
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsUtils
import io.paritytech.polkadotapp.feature_dotns_api.presentation.DotNsWebViewClient
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationResult

/**
 * WebViewClient for browser environments (SPA + Explore).
 *
 * Serves .dot domains from local storage via [DotNsWebViewClient].
 * Delegates navigation decisions to [NavigationPolicy], passing a pre-classified [DotNsNavigationType].
 */
class BrowserWebViewClient(
    dotNsResolver: DotNsResolver,
    private val navigationPolicy: NavigationPolicy,
) : DotNsWebViewClient(dotNsResolver) {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val origin = view.url?.toUri()
        val destination = request.url
        val type = DotNsUtils.classifyNavigation(origin, destination)
        val result = navigationPolicy.handleNavigation(type, destination)

        return when (result) {
            NavigationResult.INTERCEPTED_BY_POLICY -> true
            NavigationResult.DELEGATE_TO_WEBVIEW -> false
        }
    }
}
