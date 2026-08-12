package io.paritytech.polkadotapp.feature_products_impl.domain.browser

import android.net.Uri
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApiImpl
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiEnvironment
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiSession
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostCallGroupFactory
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.PageLoadInjection
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import io.paritytech.polkadotapp.feature_products_impl.domain.jsRuntime.WebViewRuntime
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Builds the live runtime for a single browser tab — the WebView provider plus its host-api session — all
 * hosted in the tab's [CoroutineScope] (cancelling it disposes the session and destroys the WebView).
 *
 * This isolates the dense JS-bridge wiring; [RealProductSessionController] just attaches its listeners to
 * the returned provider.
 */
class ProductTabSessionFactory @Inject constructor(
    private val browserWebViewProviderFactory: BrowserWebViewProvider.Factory,
    private val hostCallGroupFactory: HostCallGroupFactory,
    private val sessionFactory: HostApiSession.Factory,
    private val botApiFactory: ProductsBotApiImpl.Factory,
) {
    /**
     * Start a session for [url] inside [scope]; [onDeeplink] handles navigations the product itself can't
     * take. Returns the provider the caller wires its page/progress listeners to.
     */
    fun create(url: String, scope: CoroutineScope, onDeeplink: (Uri) -> Unit): BrowserWebViewProvider {
        val provider = browserWebViewProviderFactory.create(
            url,
            NavigationPolicy.InlineNavigation(onDeeplinkNavigation = onDeeplink),
            scope,
        )

        val hostApiNavigation = NavigationPolicy.HostApiNavigation(
            onDeeplinkNavigation = onDeeplink,
            webViewLoader = { target -> scope.launch { provider.getWebView()?.loadUrl(target) } },
        )

        val callingProductIdProvider = provider.callingProductIdProvider
        val botApi = botApiFactory.create(callingProductIdProvider)
        val runtime = WebViewRuntime(provider)
        val transport = runtime.createTransport()
        val handlerGroups = hostCallGroupFactory.createShared(botApi, callingProductIdProvider, hostApiNavigation)
        val environment = HostApiEnvironment(
            injectionStrategy = PageLoadInjection(pageLifecycleSource = provider, coroutineScope = scope),
            handlerGroups = handlerGroups,
        )
        val session = sessionFactory.create(environment, runtime, transport, scope)

        scope.launch {
            runCatching { session.initialize() }
                .onFailure { Timber.e(it, "Failed to initialize browser tab") }
        }

        return provider
    }
}
