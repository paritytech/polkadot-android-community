package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser

import kotlinx.coroutines.flow.StateFlow

interface SpaBrowserContract {
    val state: StateFlow<SpaBrowserUiState>

    fun onCloseClick()
    fun onMoreClicked()
    fun onMoreMenuDismissed()
    fun onOpenChatClick()
    fun onRefreshClick()
    fun onShareClick()
    fun onBackPressed()
}

data class SpaBrowserUiState(
    val title: String? = "",
    val subtitle: String? = "",
    val isMoreMenuVisible: Boolean = false,
    val canOpenChat: Boolean = false,
)
