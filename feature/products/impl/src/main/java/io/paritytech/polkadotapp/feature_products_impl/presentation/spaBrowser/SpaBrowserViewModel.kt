package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser

import android.webkit.WebView
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.deeplink.DeepLinkHandler
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.presentation.sharing.SharingManager
import io.paritytech.polkadotapp.common.utils.Urls
import io.paritytech.polkadotapp.common.utils.capitalize
import io.paritytech.polkadotapp.common.utils.disable
import io.paritytech.polkadotapp.common.utils.enable
import io.paritytech.polkadotapp.common.utils.flowOf
import io.paritytech.polkadotapp.common.utils.shareInBackground
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApiImpl
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiEnvironment
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiSession
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostCallGroupFactory
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.PageLoadInjection
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import io.paritytech.polkadotapp.feature_products_impl.domain.jsRuntime.WebViewRuntime
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductRegistrar
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductScriptResolver
import io.paritytech.polkadotapp.feature_products_impl.domain.spaBrowser.SpaBrowserInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.BrowserWebViewProvider
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URI
import javax.inject.Inject

@HiltViewModel
class SpaBrowserViewModel @Inject constructor(
    private val browserWebViewProviderFactory: BrowserWebViewProvider.Factory,
    private val hostCallGroupFactory: HostCallGroupFactory,
    private val sessionFactory: HostApiSession.Factory,
    private val botApiFactory: ProductsBotApiImpl.Factory,
    private val productRegistrar: ProductRegistrar,
    private val productScriptResolver: ProductScriptResolver,
    private val sharingManager: SharingManager,
    private val router: ProductsRouter,
    private val deepLinkHandler: DeepLinkHandler,
    private val spaBrowserInteractor: SpaBrowserInteractor,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel(), SpaBrowserContract {
    private val payload: SpaBrowserPayload = savedStateHandle.getPayload()

    private val isMoreMenuVisible = MutableStateFlow(false)
    private val titleFlow = MutableStateFlow("")
    private val urlFlow = MutableStateFlow(payload.url)
    private val currentProductId = urlFlow
        .map { url -> ProductId.fromUrl(url.toUri()) }
        .shareInBackground()

    private val canOpenChatFlow = currentProductId.map { result ->
        val productId = result.getOrNull() ?: return@map false
        productScriptResolver.canResolveScript(productId)
    }.shareInBackground()

    override val state: StateFlow<SpaBrowserUiState> = combine(
        isMoreMenuVisible,
        titleFlow,
        urlFlow,
        canOpenChatFlow
    ) { menuVisible, title, url, canOpenChat ->
        val appHost: String? = URI(url).host
        SpaBrowserUiState(
            title = title.ifEmpty { appHost?.capitalize() },
            subtitle = appHost,
            isMoreMenuVisible = menuVisible,
            canOpenChat = canOpenChat,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SpaBrowserUiState())

    private data class SessionComponents(
        val session: HostApiSession,
        val provider: BrowserWebViewProvider,
    )

    private val componentsFlow: Flow<SessionComponents> = flowOf {
        createComponents()
    }.shareInBackground()

    val webView: StateFlow<WebView?> = componentsFlow
        .map { it.provider.getWebView() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        initializeSession()
        subscribeTitleUpdate()
        subscribeProductRegistration()
    }

    override fun onCloseClick() {
        router.back()
    }

    override fun onMoreClicked() {
        isMoreMenuVisible.enable()
    }

    override fun onMoreMenuDismissed() {
        isMoreMenuVisible.disable()
    }

    override fun onOpenChatClick() {
        isMoreMenuVisible.disable()

        launch {
            val productId = currentProductId.first().getOrNull() ?: return@launch
            spaBrowserInteractor.installChatAndAwaitRoomCreated(productId)
                .onSuccess { chatId -> router.openChat(chatId) }
                .onFailure { showMessage(it.message ?: "Failed to open chat") }
        }
    }

    override fun onRefreshClick() {
        isMoreMenuVisible.disable()
        webView.value?.reload()
    }

    override fun onShareClick() {
        isMoreMenuVisible.disable()
        sharingManager.shareText(Urls.ensureHttpsProtocol(urlFlow.value))
    }

    override fun onBackPressed() {
        val wv = webView.value
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            router.back()
        }
    }

    fun pauseConnections() {
        launch { componentsFlow.first().provider.pauseConnections() }
    }

    fun resumeConnections() {
        launch { componentsFlow.first().provider.resumeConnections() }
    }

    private fun createComponents(): SessionComponents {
        val navigationPolicy = NavigationPolicy.InlineNavigation(
            webViewLoader = { url -> launch { webView.value?.loadUrl(url) } },
            onCrossProductNavigation = { uri -> launch { deepLinkHandler.handle(uri) } },
        )
        val webViewProvider = browserWebViewProviderFactory.create(payload.url, navigationPolicy, viewModelScope)
        webViewProvider.addOnPageStartedListener { urlFlow.value = it }
        val callingProductIdProvider = webViewProvider.callingProductIdProvider

        val botApi = botApiFactory.create(callingProductIdProvider)

        val runtime = WebViewRuntime(webViewProvider)

        val transport = runtime.createTransport()
        val handlerGroups = hostCallGroupFactory.createShared(botApi, callingProductIdProvider, navigationPolicy)

        val environment = HostApiEnvironment(
            navigationPolicy = navigationPolicy,
            injectionStrategy = PageLoadInjection(
                pageLifecycleSource = webViewProvider,
                coroutineScope = viewModelScope,
            ),
            handlerGroups = handlerGroups,
        )

        val session = sessionFactory.create(environment, runtime, transport, viewModelScope)

        return SessionComponents(session, webViewProvider)
    }

    private fun initializeSession() {
        launch {
            runCatching {
                val components = componentsFlow.first()
                components.session.initialize()
            }.onFailure { Timber.e(it, "Failed to initialize SPA session") }
        }
    }

    private fun subscribeProductRegistration() {
        currentProductId
            .mapNotNull { it.getOrNull() }
            .onEach { productRegistrar.ensureRegistered(it, contentHash = null) }
            .launchIn(viewModelScope)
    }

    private fun subscribeTitleUpdate() {
        launch {
            val components = componentsFlow.first()
            components.provider.addOnPageFinishedListener {
                titleFlow.value = webView.value?.title.orEmpty()
            }
        }
    }
}
