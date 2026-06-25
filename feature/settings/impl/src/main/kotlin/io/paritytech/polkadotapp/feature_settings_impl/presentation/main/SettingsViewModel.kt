package io.paritytech.polkadotapp.feature_settings_impl.presentation.main

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.domain.printing.ReceiptPrinter
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
    private val receiptPrinter: ReceiptPrinter,
    private val router: SettingsRouter
) : BaseViewModel() {
    private val isPrinterTestAvailable = receiptPrinter.isAvailable()

    val state: StateFlow<SettingsUiState> = combine(
        interactor.observeBackupExists(),
        interactor.subscribeHasBlockedContacts(),
        appThemeSelector.selectedTheme
    ) { backupExists, hasBlockedUsers, selectedTheme ->
        SettingsUiState(
            isDebug = BuildConfig.DEBUG,
            selectedTheme = selectedTheme,
            isBackupMissing = !backupExists,
            hasBlockedUsers = hasBlockedUsers,
            isPrinterTestAvailable = isPrinterTestAvailable
        )
    }
        .stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(
                isDebug = BuildConfig.DEBUG,
                selectedTheme = PolkadotAppTheme.DEFAULT,
                isBackupMissing = false,
                hasBlockedUsers = false,
                isPrinterTestAvailable = isPrinterTestAvailable
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

    fun onPrinterClick() {
        router.openPrinterDiagnostics()
    }
}
