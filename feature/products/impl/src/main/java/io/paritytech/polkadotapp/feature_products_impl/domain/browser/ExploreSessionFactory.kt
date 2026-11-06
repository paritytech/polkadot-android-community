package io.paritytech.polkadotapp.feature_products_impl.domain.browser

import androidx.core.net.toUri
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApiImpl
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiEnvironment
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiSession
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostCallGroupFactory
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.PageLoadInjection
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import io.paritytech.polkadotapp.feature_products_impl.domain.jsRuntime.WebViewRuntime
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductRegistrar
import io.paritytech.polkadotapp.feature_products_impl.domain.product.launchEnsureRegistered
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class ExploreSession(
    val provider: BrowserWebViewProvider,
    val hostApiSession: HostApiSession,
)

class ExploreSessionFactory @Inject constructor(
    private val browserWebViewProviderFactory: BrowserWebViewProvider.Factory,
    private val hostCallGroupFactory: HostCallGroupFactory,
    private val sessionFactory: HostApiSession.Factory,
    private val botApiFactory: ProductsBotApiImpl.Factory,
    private val productRegistrar: ProductRegistrar,
    private val dotNsTldProvider: DotNsTldProvider,
) {
    fun create(url: String, scope: CoroutineScope, onProductSelected: (ProductId) -> Unit): ExploreSession {
        val navigationPolicy = NavigationPolicy.CatalogNavigation(onProductSelected, dotNsTldProvider)

        val webViewProvider = browserWebViewProviderFactory.create(url, navigationPolicy, allowIframes = false, scope)
        webViewProvider.addOnPageStartedListener { pageUrl ->
            dotNsTldProvider.currentTldOrNull()
                ?.let { tld -> ProductId.fromUrl(pageUrl.toUri(), tld).getOrNull() }
                ?.let { with(scope) { productRegistrar.launchEnsureRegistered(it) } }
        }
        val productIdProvider = webViewProvider.callingProductIdProvider

        val runtime = WebViewRuntime(webViewProvider)

        val botApi = botApiFactory.create(productIdProvider)
        val handlerGroups = hostCallGroupFactory.createShared(botApi, productIdProvider, navigationPolicy)

        val environment = HostApiEnvironment(
            injectionStrategy = PageLoadInjection(
                pageLifecycleSource = webViewProvider,
                coroutineScope = scope,
            ),
            handlerGroups = handlerGroups,
        )

        val transport = runtime.createTransport()
        val session = sessionFactory.create(environment, runtime, transport, scope)

        return ExploreSession(webViewProvider, session)
    }
}
