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
    fun onKioskModeClicked()
    fun onKioskPinDigit(digit: Int)
    fun onKioskPinBackspace()
    fun onKioskExitTap()
    fun onKioskPinDismissed()
}

data class SpaBrowserUiState(
    val title: String? = "",
    val subtitle: String? = "",
    val isMoreMenuVisible: Boolean = false,
    val canOpenChat: Boolean = false,
    val kiosk: KioskUiState = KioskUiState(),
)

sealed interface KioskPhase {
    data object Inactive : KioskPhase
    data object SettingPin : KioskPhase
    data object Active : KioskPhase
    data object Unlocking : KioskPhase
}

data class KioskUiState(
    val phase: KioskPhase = KioskPhase.Inactive,
    val enteredDigits: Int = 0,
    val hasError: Boolean = false,
) {
    val isEngaged: Boolean get() = phase == KioskPhase.Active || phase == KioskPhase.Unlocking
    val isPromptVisible: Boolean get() = phase == KioskPhase.SettingPin || phase == KioskPhase.Unlocking
    val isSettingPin: Boolean get() = phase == KioskPhase.SettingPin
    val showExitHotspot: Boolean get() = phase == KioskPhase.Active
}
