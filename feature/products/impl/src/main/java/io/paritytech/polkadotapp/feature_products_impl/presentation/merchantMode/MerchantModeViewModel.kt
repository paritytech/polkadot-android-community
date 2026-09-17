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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val session: StateFlow<MerchantSession> = flow {
        val resolved = with(stalenessReport) { merchantProductLoader.getMerchantUrl() }
            .logFailure("Merchant mode is unavailable")
            .fold(
                onSuccess = { MerchantSession.Ready(spaHost.createSession(it)) },
                onFailure = { MerchantSession.Unavailable },
            )

        emit(resolved)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MerchantSession.Resolving)

    val webView: StateFlow<WebView?> = session
        .flatMapLatest { current -> current.spaSessionOrNull()?.webView ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override val state: StateFlow<MerchantModePageState> = session
        .flatMapLatest { current ->
            when (current) {
                MerchantSession.Resolving -> flowOf(resolvingState)
                MerchantSession.Unavailable -> flowOf(MerchantModePageState.Unavailable)
                is MerchantSession.Ready -> current.spaSession.loadProgress.scan(resolvingState) { previous, progress ->
                    previous.next(progress)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolvingState)

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
        launch { awaitSpaSession()?.pauseConnections() }
    }

    fun resumeConnections() {
        launch { awaitSpaSession()?.resumeConnections() }
    }

    private suspend fun awaitSpaSession(): SpaHostSession? {
        return session.first { it != MerchantSession.Resolving }.spaSessionOrNull()
    }
}

private val resolvingState: MerchantModePageState = MerchantModePageState.Loading(DotNsLoadProgress.Resolving)

private sealed interface MerchantSession {
    data object Resolving : MerchantSession
    data object Unavailable : MerchantSession
    data class Ready(val spaSession: SpaHostSession) : MerchantSession
}

private fun MerchantSession.spaSessionOrNull(): SpaHostSession? = (this as? MerchantSession.Ready)?.spaSession

// Once the first archive is served the terminal owns the screen; its later navigations must not blank it out.
internal fun MerchantModePageState.next(progress: DotNsLoadProgress): MerchantModePageState = when {
    this == MerchantModePageState.Content -> MerchantModePageState.Content
    progress == DotNsLoadProgress.Completed -> MerchantModePageState.Content
    progress is DotNsLoadProgress.Failed -> MerchantModePageState.Unavailable
    else -> MerchantModePageState.Loading(progress)
}
