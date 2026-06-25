@file:OptIn(ExperimentalTime::class)

package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser

import android.webkit.WebView
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.domain.kiosk.KioskLockController
import io.paritytech.polkadotapp.common.domain.model.CurrentTimeContext
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
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
    private val currentTimeContext: CurrentTimeContext,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel(), SpaBrowserContract {
    private val payload: SpaBrowserPayload = savedStateHandle.getPayload()

    private val isMoreMenuVisible = MutableStateFlow(false)
    private val kioskPhase = MutableStateFlow<KioskPhase>(KioskPhase.Inactive)
    private val enteredPin = MutableStateFlow("")
    private val kioskError = MutableStateFlow(false)
    private var exitTapCount = 0
    private var lastExitTapTime: Instant? = null
    private val titleFlow = MutableStateFlow("")
    private val urlFlow = MutableStateFlow(payload.url)
    private val currentProductId = urlFlow
        .map { url -> ProductId.fromUrl(url.toUri()) }
        .shareInBackground()

    private val canOpenChatFlow = currentProductId.map { result ->
        val productId = result.getOrNull() ?: return@map false
        productScriptResolver.canResolveScript(productId)
    }.shareInBackground()

    private val kioskStateFlow = combine(
        kioskPhase,
        enteredPin,
        kioskError,
    ) { phase, pin, hasError ->
        KioskUiState(phase = phase, enteredDigits = pin.length, hasError = hasError)
    }

    override val state: StateFlow<SpaBrowserUiState> = combine(
        isMoreMenuVisible,
        titleFlow,
        urlFlow,
        canOpenChatFlow,
        kioskStateFlow,
    ) { menuVisible, title, url, canOpenChat, kiosk ->
        val appHost: String? = URI(url).host
        SpaBrowserUiState(
            title = title.ifEmpty { appHost?.capitalize() },
            subtitle = appHost,
            isMoreMenuVisible = menuVisible,
            canOpenChat = canOpenChat,
            kiosk = kiosk,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SpaBrowserUiState())

    init {
        // Drive the host's device-owner Lock Task from the in-app kiosk state:
        // the device is OS-locked only while the kiosk is Active/Unlocking.
        kioskPhase
            .onEach { phase ->
                KioskLockController.engaged.value =
                    phase == KioskPhase.Active || phase == KioskPhase.Unlocking
            }
            .launchIn(viewModelScope)
    }

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
        when (kioskPhase.value) {
            KioskPhase.SettingPin, KioskPhase.Unlocking -> onKioskPinDismissed()
            KioskPhase.Active -> Unit
            KioskPhase.Inactive -> {
                val wv = webView.value
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    router.back()
                }
            }
        }
    }

    override fun onKioskModeClicked() {
        isMoreMenuVisible.disable()
        kioskError.value = false
        enteredPin.value = ""
        kioskPhase.value = KioskPhase.SettingPin
    }

    override fun onKioskPinDigit(digit: Int) {
        if (enteredPin.value.length >= PIN_LENGTH) return
        kioskError.value = false
        val updated = enteredPin.value + digit.toString()
        enteredPin.value = updated
        if (updated.length < PIN_LENGTH) return

        when (kioskPhase.value) {
            KioskPhase.SettingPin -> commitNewPin(updated)
            KioskPhase.Unlocking -> attemptUnlock(updated)
            else -> Unit
        }
    }

    override fun onKioskPinBackspace() {
        kioskError.value = false
        enteredPin.value = enteredPin.value.dropLast(1)
    }

    override fun onKioskExitTap() {
        if (kioskPhase.value != KioskPhase.Active) {
            Timber.i("KioskExit: tap ignored (phase=${kioskPhase.value})")
            return
        }
        val now = currentTimeContext.currentTime()
        val withinWindow = lastExitTapTime?.let { now - it <= EXIT_TAP_WINDOW } ?: false
        exitTapCount = if (withinWindow) exitTapCount + 1 else 1
        lastExitTapTime = now
        Timber.i("KioskExit: tap #$exitTapCount/$EXIT_REQUIRED_TAPS (withinWindow=$withinWindow)")
        if (exitTapCount < EXIT_REQUIRED_TAPS) return
        exitTapCount = 0
        kioskError.value = false
        enteredPin.value = ""
        kioskPhase.value = KioskPhase.Unlocking
        Timber.i("KioskExit: threshold reached -> Unlocking (PIN prompt)")
    }

    override fun onKioskPinDismissed() {
        enteredPin.value = ""
        kioskError.value = false
        kioskPhase.value = when (kioskPhase.value) {
            KioskPhase.Unlocking -> KioskPhase.Active
            else -> KioskPhase.Inactive
        }
    }

    fun pauseConnections() {
        launch { componentsFlow.first().provider.pauseConnections() }
    }

    fun resumeConnections() {
        launch { componentsFlow.first().provider.resumeConnections() }
    }

    private fun commitNewPin(pin: String) {
        launch {
            spaBrowserInteractor.saveKioskPin(pin)
                .onSuccess {
                    enteredPin.value = ""
                    kioskError.value = false
                    kioskPhase.value = KioskPhase.Active
                }
                .onFailure {
                    Timber.e(it, "Failed to save kiosk PIN")
                    enteredPin.value = ""
                    kioskPhase.value = KioskPhase.Inactive
                }
        }
    }

    private fun attemptUnlock(pin: String) {
        launch {
            if (spaBrowserInteractor.verifyKioskPin(pin)) {
                spaBrowserInteractor.clearKioskPin()
                enteredPin.value = ""
                kioskError.value = false
                kioskPhase.value = KioskPhase.Inactive
            } else {
                enteredPin.value = ""
                kioskError.value = true
            }
        }
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

    private companion object {
        const val PIN_LENGTH = 4
        const val EXIT_REQUIRED_TAPS = 5
        val EXIT_TAP_WINDOW = 2.seconds
    }
}
