package io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode

import android.webkit.WebView
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReport
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import io.paritytech.polkadotapp.feature_products_api.presentation.spaHost.SpaHost
import io.paritytech.polkadotapp.feature_products_api.presentation.spaHost.SpaHostSession
import io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode.MerchantProductLoader
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MerchantModeViewModel @Inject constructor(
    spaHost: SpaHost,
    merchantProductLoader: MerchantProductLoader,
    private val router: ProductsRouter,
) : BaseViewModel(), MerchantModeContract {
    override val stalenessReport = StalenessReport(this)

    private val host: StateFlow<MerchantHost> = flow {
        val resolved = with(stalenessReport) { merchantProductLoader.getMerchantUrl() }
            .logFailure("Merchant mode is unavailable")
            .fold(
                onSuccess = { MerchantHost.Ready(spaHost.createSession(it)) },
                onFailure = { MerchantHost.Unavailable },
            )

        emit(resolved)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MerchantHost.Resolving)

    private val connectionsResumed = MutableStateFlow(true)

    val webView: StateFlow<WebView?> = host
        .flatMapLatest { current -> current.sessionOrNull()?.webView ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override val state: StateFlow<MerchantModeUiState> = host
        .flatMapLatest { current ->
            when (current) {
                MerchantHost.Resolving -> flowOf(resolvingState)

                MerchantHost.Unavailable -> flowOf(
                    MerchantModeUiState(
                        loadProgress = DotNsLoadProgress.Idle,
                        pageState = MerchantModePageState.Unavailable,
                    )
                )

                is MerchantHost.Ready -> current.session.loadProgress
                    .scan(resolvingState) { previous, progress ->
                        MerchantModeUiState(loadProgress = progress, pageState = previous.pageState.next(progress))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolvingState)

    init {
        applyLifecycleToSession()
    }

    override fun onCloseClick() {
        router.back()
    }

    override fun onBackPressed() {
        val currentWebView = webView.value
        if (currentWebView != null && currentWebView.canGoBack()) {
            currentWebView.goBack()
        } else {
            router.back()
        }
    }

    fun pauseConnections() {
        connectionsResumed.value = false
    }

    fun resumeConnections() {
        connectionsResumed.value = true
    }

    // The screen can be backgrounded while the address is still resolving, so the lifecycle is applied
    // once the session exists rather than when the call arrives.
    private fun applyLifecycleToSession() = launch {
        val session = host.filterIsInstance<MerchantHost.Ready>().first().session

        connectionsResumed.collect { resumed ->
            if (resumed) session.resumeConnections() else session.pauseConnections()
        }
    }
}

private val resolvingState = MerchantModeUiState(
    loadProgress = DotNsLoadProgress.Resolving,
    pageState = MerchantModePageState.Loading,
)

private sealed interface MerchantHost {
    data object Resolving : MerchantHost
    data object Unavailable : MerchantHost
    data class Ready(val session: SpaHostSession) : MerchantHost
}

private fun MerchantHost.sessionOrNull(): SpaHostSession? = (this as? MerchantHost.Ready)?.session

// Once the first archive is served the terminal owns the screen — navigations it makes afterwards
// must not blank it back out.
private fun MerchantModePageState.next(progress: DotNsLoadProgress): MerchantModePageState = when {
    this == MerchantModePageState.Content -> MerchantModePageState.Content
    progress == DotNsLoadProgress.Completed -> MerchantModePageState.Content
    progress is DotNsLoadProgress.Failed -> MerchantModePageState.Unavailable
    else -> MerchantModePageState.Loading
}
