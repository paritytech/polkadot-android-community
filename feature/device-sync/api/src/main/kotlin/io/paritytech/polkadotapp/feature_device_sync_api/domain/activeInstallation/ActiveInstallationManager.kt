package io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation

import kotlinx.coroutines.flow.StateFlow

interface ActiveInstallationManager {
    val status: StateFlow<InstallationStatus>

    suspend fun activateThisInstallation(): Result<Unit>
}
