package io.paritytech.polkadotapp.feature_settings_impl.presentation.main

import android.os.Build
import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.designsystem.themes.PolkadotAppTheme
import io.paritytech.polkadotapp.feature_settings_impl.BuildConfig

@Immutable
data class SettingsUiState(
    val isDebug: Boolean = BuildConfig.DEBUG,
    val selectedTheme: PolkadotAppTheme,
    val isLanguageSettingsAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    val isBackupMissing: Boolean = false,
    val hasBlockedUsers: Boolean = false,
)
