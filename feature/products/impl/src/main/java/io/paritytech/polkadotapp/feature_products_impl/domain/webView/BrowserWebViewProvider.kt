package io.paritytech.polkadotapp.feature_products_impl.domain.webView

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.CallingProductIdProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.PageLifecycleSource
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.UrlDerivedProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import kotlinx.coroutines.CoroutineScope

/**
 * Visible WebView provider for SPA browser and Explore environments.
 *
 * Handles WebView lifecycle, DotNs content serving, permission checks, and navigation.
 * Does NOT inject scripts — that responsibility belongs to [PageLoadInjection]
 * which hooks into this provider via [PageLifecycleSource].
 *
 * Replaces [SpaProductWebViewProvider] and [SpaExplorerWebViewProvider].
 */
class BrowserWebViewProvider @AssistedInject constructor(
    @param:ApplicationContext private val context: Context,
    private val productWebChromeClientFactory: ProductWebChromeClient.Factory,
    private val webViewPermissionClientFactory: WebViewPermissionClientFactory,
    private val dotNsResolver: DotNsResolver,
    dispatchers: CoroutineDispatchers,
    @Assisted private val initialUrl: String,
    @Assisted private val navigationPolicy: NavigationPolicy,
    @Assisted private val scope: CoroutineScope,
) : WebViewProvider(dispatchers), PageLifecycleSource {
    @AssistedFactory
    interface Factory {
        fun create(
            initialUrl: String,
            navigationPolicy: NavigationPolicy,
            scope: CoroutineScope
        ): BrowserWebViewProvider
    }

    override val callingProductIdProvider: CallingProductIdProvider = UrlDerivedProductId {
        accessWebView(WebView::getUrl)
    }

    private val permissionClient = webViewPermissionClientFactory.create(callingProductIdProvider)
    private val chromeClient = productWebChromeClientFactory.create(
        logPrefix = "Browser: $initialUrl",
        callingProductIdProvider = callingProductIdProvider,
        scope = scope,
        onTitleReceived = null,
    )

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun createWebView(): WebView {
        return WebView(context).apply {
            // This is needed so webView has a proper initial viewport height when the page renders. With overflow: hidden and height: 100%, if the WebView's layout height isn't resolved yet, the
            //  content box collapses to 0px
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
            }

            val innerClient = BrowserWebViewClient(dotNsResolver, navigationPolicy)
            webViewClient = InternalWebViewClient(innerClient)
            webChromeClient = chromeClient
        }
    }

    override suspend fun loadInitialContent() {
        accessWebView { it.loadUrl(initialUrl) }
    }

    private inner class InternalWebViewClient(
        private val innerClient: WebViewClient,
    ) : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            return permissionClient.shouldInterceptRequest(view, request)
                ?: innerClient.shouldInterceptRequest(view, request)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return innerClient.shouldOverrideUrlLoading(view, request)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            permissionClient.onPageStarted(view, url, favicon)
            innerClient.onPageStarted(view, url, favicon)
            notifyOnPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            innerClient.onPageFinished(view, url)
            notifyOnPageFinished()
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            resetWebView()
            notifyRenderProcessGone()
            return true
        }
    }
}
