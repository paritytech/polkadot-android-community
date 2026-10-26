package io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation

sealed interface InstallationStatus {
    data object Unknown : InstallationStatus

    data object Active : InstallationStatus

    data class Inactive(val activeInstallation: ActiveInstallation) : InstallationStatus

    data object Unreadable : InstallationStatus
}
