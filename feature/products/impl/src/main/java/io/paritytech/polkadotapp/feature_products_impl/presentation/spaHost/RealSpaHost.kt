package io.paritytech.polkadotapp.feature_products_impl.presentation.spaHost

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.deeplink.DeepLinkHandler
import io.paritytech.polkadotapp.common.presentation.deeplink.handleAndProcessOutcomeWithSystemFallback
import io.paritytech.polkadotapp.common.presentation.screens.MessageDisplay
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.presentation.spaHost.SpaHost
import io.paritytech.polkadotapp.feature_products_api.presentation.spaHost.SpaHostSession
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApiImpl
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiEnvironment
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiSession
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostCallGroupFactory
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.PageLoadInjection
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import io.paritytech.polkadotapp.feature_products_impl.domain.jsRuntime.WebViewRuntime
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductRegistrar
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealSpaHost @Inject constructor(
    private val browserWebViewProviderFactory: BrowserWebViewProvider.Factory,
    private val hostCallGroupFactory: HostCallGroupFactory,
    private val sessionFactory: HostApiSession.Factory,
    private val botApiFactory: ProductsBotApiImpl.Factory,
    private val productRegistrar: ProductRegistrar,
    private val deepLinkHandler: DeepLinkHandler,
    @param:ApplicationContext private val context: Context,
) : SpaHost {
    context(scope: ComputationalScope, messageDisplay: MessageDisplay)
    override fun createSession(initialUrl: String): SpaHostSession {
        lateinit var webViewProvider: BrowserWebViewProvider

        val webViewNavigation = NavigationPolicy.InlineNavigation(
            onDeeplinkNavigation = { launchDeeplinkNavigation(it) }
        )
        val hostApiNavigation = NavigationPolicy.HostApiNavigation(
            onDeeplinkNavigation = { launchDeeplinkNavigation(it) },
            webViewLoader = { scope.launch { webViewProvider.getWebView().loadUrl(it) } }
        )

        webViewProvider = browserWebViewProviderFactory.create(
            initialUrl = initialUrl,
            navigationPolicy = webViewNavigation,
            scope = scope
        )

        val callingProductIdProvider = webViewProvider.callingProductIdProvider
        val botApi = botApiFactory.create(callingProductIdProvider)
        val runtime = WebViewRuntime(webViewProvider)
        val transport = runtime.createTransport()
        val handlerGroups = hostCallGroupFactory.createShared(
            botApi = botApi,
            productIdProvider = callingProductIdProvider,
            navigationPolicy = hostApiNavigation
        )

        val environment = HostApiEnvironment(
            injectionStrategy = PageLoadInjection(
                pageLifecycleSource = webViewProvider,
                coroutineScope = scope,
            ),
            handlerGroups = handlerGroups,
        )

        val session = sessionFactory.create(environment, runtime, transport, scope)
        scope.launch {
            runCatching { session.initialize() }
                .logFailure("Failed to initialize SPA host session")
        }

        webViewProvider.addOnPageStartedListener { url ->
            scope.launch {
                ProductId.fromUrl(url.toUri()).getOrNull()?.let {
                    productRegistrar.ensureRegistered(it, contentHash = null)
                }
            }
        }

        val webViewFlow: StateFlow<WebView?> = flow { emit(webViewProvider.getWebView()) }
            .stateIn(scope, SharingStarted.Eagerly, null)

        return RealSpaHostSession(webViewFlow, webViewProvider)
    }

    context(scope: ComputationalScope, messageDisplay: MessageDisplay)
    private fun launchDeeplinkNavigation(data: Uri) {
        scope.launch {
            with(context) {
                deepLinkHandler.handleAndProcessOutcomeWithSystemFallback(data)
            }
        }
    }
}

private class RealSpaHostSession(
    override val webView: StateFlow<WebView?>,
    private val provider: BrowserWebViewProvider,
) : SpaHostSession {
    override fun pauseConnections() {
        provider.pauseConnections()
    }

    override fun resumeConnections() {
        provider.resumeConnections()
    }
}
