package io.paritytech.polkadotapp.feature_settings_impl.presentation.main

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.design.theme.AppThemeSelector
import io.paritytech.polkadotapp.designsystem.themes.PolkadotAppTheme
import io.paritytech.polkadotapp.feature_settings_impl.BuildConfig
import io.paritytech.polkadotapp.feature_settings_impl.SettingsRouter
import io.paritytech.polkadotapp.feature_settings_impl.domain.settings.SettingsInteractor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    interactor: SettingsInteractor,
    appThemeSelector: AppThemeSelector,
    private val router: SettingsRouter
) : BaseViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        interactor.observeBackupExists(),
        interactor.subscribeHasBlockedContacts(),
        appThemeSelector.selectedTheme
    ) { backupExists, hasBlockedUsers, selectedTheme ->
        SettingsUiState(
            isDebug = BuildConfig.DEBUG,
            selectedTheme = selectedTheme,
            isBackupMissing = !backupExists,
            hasBlockedUsers = hasBlockedUsers
        )
    }
        .stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(
                isDebug = BuildConfig.DEBUG,
                selectedTheme = PolkadotAppTheme.DEFAULT,
                isBackupMissing = false,
                hasBlockedUsers = false
            )
        )

    fun onBackupClick() {
        router.openBackup()
    }

    fun onLinkedDevicesClick() {
        router.openLinkedDevices()
    }

    fun onForceReclaimClick() {
        router.openForceReclaim()
    }

    fun onPrivacyPolicyClick() {
        router.openPrivacyPolicy()
    }

    fun onTermsOfUseClick() {
        router.openTermsOfUse()
    }

    fun onProductsClick() {
        router.openProductSettings()
    }

    fun onBlockedUsersClick() {
        router.openBlockedUsers()
    }

    fun onNotificationsClick() {
        router.openNotificationSettings()
    }

    fun onThemeClick() {
        router.openThemes()
    }

    fun onDebugMenuClick() {
        router.openDebugMenu()
    }
}
