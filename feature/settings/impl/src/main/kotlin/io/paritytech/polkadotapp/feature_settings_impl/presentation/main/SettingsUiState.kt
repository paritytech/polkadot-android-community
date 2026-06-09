package io.paritytech.polkadotapp.feature_settings_impl.presentation.main

import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.designsystem.themes.PolkadotAppTheme

@Immutable
data class SettingsUiState(
    val isDebug: Boolean,
    val selectedTheme: PolkadotAppTheme,
    val isBackupMissing: Boolean = false,
    val hasBlockedUsers: Boolean = false,
)
