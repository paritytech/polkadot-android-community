package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser

import android.webkit.WebView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.feature_products_api.domain.browser.ProductSessionController
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.presentation.SpaBrowserPayload
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The browser fragment's presentation: activates the product session for its payload and shows the active
 * tab (attaching its live WebView).
 */
@HiltViewModel
class SpaBrowserViewModel @Inject constructor(
    private val productSessionController: ProductSessionController,
    private val router: ProductsRouter,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel(), SpaBrowserContract {
    val webView: StateFlow<WebView?> = productSessionController.webView

    override val state: StateFlow<SpaBrowserUiState> = combine(
        productSessionController.activeTab,
        productSessionController.loadProgress,
    ) { info, loadProgress ->
        SpaBrowserUiState(
            title = info?.title,
            subtitle = info?.host,
            loadProgress = loadProgress,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SpaBrowserUiState())

    init {
        // Activate the session for this screen's payload. With no payload (opened from the tab bar) the
        // active tab is already selected — just make sure its WebView is live.
        when (val payload = savedStateHandle.get<SpaBrowserPayload>(SpaBrowserPayload::class.java.name)) {
            is SpaBrowserPayload.ByProductId -> productSessionController.openProduct(ProductId.fromStoredValue(payload.productId))
            is SpaBrowserPayload.ByUrl -> productSessionController.openUrl(payload.url)
            null -> productSessionController.ensureActiveLive()
        }

        quitOnLastTabClosed()
    }

    // The visit history spans one stay in the browser: whichever way the screen was left (back, the tab bar,
    // a deeplink), the next tab opened starts its own back chain.
    override fun onCleared() {
        super.onCleared()

        productSessionController.resetVisitHistory()
    }

    // Back unwinds the active tab's page history first, then the tab visit history (tabs stay open), and
    // only leaves the browser once neither has anything left.
    override fun onBackPressed() {
        val wv = webView.value
        when {
            wv != null && wv.canGoBack() -> wv.goBack()
            productSessionController.goToPreviousTab() -> Unit
            else -> router.leaveBrowser()
        }
    }

    private fun quitOnLastTabClosed() {
        launch {
            productSessionController.openTabs
                // Wait until at least one tab is there to account for potential races during first tab opening
                .dropWhile { it.isEmpty() }
                .first { it.isEmpty() }

            router.leaveBrowser()
        }
    }
}
