package io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode

import android.webkit.WebView
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReport
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import io.paritytech.polkadotapp.feature_products_api.domain.error.ProductResolutionError
import io.paritytech.polkadotapp.feature_products_api.model.ResolvedProduct
import io.paritytech.polkadotapp.feature_products_api.presentation.spaHost.SpaHost
import io.paritytech.polkadotapp.feature_products_api.presentation.spaHost.SpaHostSession
import io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode.MerchantProductLoader
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.feature_products_impl.presentation.productLoad.PageLoad
import io.paritytech.polkadotapp.feature_products_impl.presentation.productLoad.hasAppSurface
import io.paritytech.polkadotapp.feature_products_impl.presentation.productLoad.toProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        val opened = with(stalenessReport) { merchantProductLoader.openMerchantProduct() }
            .logFailure("Merchant mode is unavailable")
            .fold(
                onSuccess = { MerchantHost.Ready(it.resolved, spaHost.createSession(it.url)) },
                onFailure = { MerchantHost.Unavailable },
            )

        emit(opened)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MerchantHost.Resolving)

    val webView: StateFlow<WebView?> = host
        .flatMapLatest { current -> current.sessionOrNull()?.webView ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val pageLoad: Flow<PageLoad> = host.flatMapLatest { current ->
        when (current) {
            MerchantHost.Resolving -> flowOf(PageLoad.Resolving)
            MerchantHost.Unavailable -> flowOf(PageLoad.Failed(ProductResolutionError.Unknown))
            is MerchantHost.Ready -> current.session.loadProgress.map { PageLoad.Serving(current.resolved, it) }
        }
    }

    override val state: StateFlow<MerchantModePageState> = pageLoad
        .scan(RESOLVING_STATE) { previous, load -> previous.next(load) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RESOLVING_STATE)

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
        launch { awaitSession()?.pauseConnections() }
    }

    fun resumeConnections() {
        launch { awaitSession()?.resumeConnections() }
    }

    private suspend fun awaitSession(): SpaHostSession? =
        host.first { it != MerchantHost.Resolving }.sessionOrNull()
}

private val RESOLVING_STATE: MerchantModePageState = MerchantModePageState.Loading(DotNsLoadProgress.Resolving)

private sealed interface MerchantHost {
    data object Resolving : MerchantHost
    data object Unavailable : MerchantHost
    data class Ready(val resolved: ResolvedProduct, val session: SpaHostSession) : MerchantHost
}

private fun MerchantHost.sessionOrNull(): SpaHostSession? = (this as? MerchantHost.Ready)?.session

// Once the first archive is served the terminal owns the screen; its later navigations must not blank it out.
internal fun MerchantModePageState.next(load: PageLoad): MerchantModePageState {
    if (this == MerchantModePageState.Content) return MerchantModePageState.Content

    return when (load) {
        is PageLoad.Failed -> MerchantModePageState.Unavailable

        is PageLoad.Serving -> when {
            !load.hasAppSurface() || load.progress is DotNsLoadProgress.Failed -> MerchantModePageState.Unavailable
            load.progress == DotNsLoadProgress.Completed -> MerchantModePageState.Content
            else -> MerchantModePageState.Loading(load.progress)
        }

        PageLoad.Resolving, PageLoad.NotAProduct -> MerchantModePageState.Loading(load.toProgress())
    }
}
