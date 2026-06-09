package io.paritytech.polkadotapp.feature_products_impl.domain.webView

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.toUrl
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.FixedProductId
import kotlinx.coroutines.CoroutineScope

class ChatWebViewProvider @AssistedInject constructor(
    @param:ApplicationContext private val context: Context,
    productWebChromeClientFactory: ProductWebChromeClient.Factory,
    webViewPermissionClientFactory: WebViewPermissionClientFactory,
    dispatchers: CoroutineDispatchers,
    @Assisted private val productId: ProductId,
    @Assisted private val scope: CoroutineScope,
) : WebViewProvider(dispatchers) {
    @AssistedFactory
    interface Factory {
        fun create(productId: ProductId, scope: CoroutineScope): ChatWebViewProvider
    }

    override val callingProductIdProvider = FixedProductId(productId)

    private val permissionClient = webViewPermissionClientFactory.create(callingProductIdProvider)
    private val chromeClient = productWebChromeClientFactory.create(
        logPrefix = "Script $productId",
        callingProductIdProvider = callingProductIdProvider,
        scope = scope,
        onTitleReceived = null,
    )

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun createWebView(): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    notifyOnPageFinished()
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    notifyOnPageStarted(url)
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    notifyRenderProcessGone()
                    resetWebView()
                    return true
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest?): WebResourceResponse? {
                    return permissionClient.shouldInterceptRequest(view, request)
                }
            }
            webChromeClient = chromeClient
        }
    }

    override suspend fun loadInitialContent() {
        accessWebView {
            it.loadDataWithBaseURL(
                getBaseUrl(),
                """
                    <!DOCTYPE html>
                    <html>
                    <head></head>
                    <body></body>
                    </html>
                """.trimIndent(),
                "text/html",
                "UTF-8",
                null,
            )
        }
    }

    private fun getBaseUrl() = productId.toUrl()
}
