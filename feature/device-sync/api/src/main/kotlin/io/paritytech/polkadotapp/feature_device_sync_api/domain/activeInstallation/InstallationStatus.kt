package io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation

sealed interface InstallationStatus {
    /** The record wasn't read yet, or the store is unreachable. */
    data object Unknown : InstallationStatus

    data object Active : InstallationStatus

    /** [activeInstallation] is the account's active installation; this one is blocked until it becomes active. */
    data class Inactive(val activeInstallation: ActiveInstallation) : InstallationStatus

    /** Records exist but none can be read: another key or an unknown version. */
    data object Unreadable : InstallationStatus
}
