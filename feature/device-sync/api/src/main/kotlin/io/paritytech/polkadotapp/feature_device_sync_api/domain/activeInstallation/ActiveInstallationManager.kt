package io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface ActiveInstallationManager {
    val status: StateFlow<InstallationStatus>

    suspend fun activateThisInstallation(): Result<Unit>
}

fun ActiveInstallationManager.isActive(): Boolean = status.value == InstallationStatus.Active

suspend fun ActiveInstallationManager.runWhileActive(block: suspend CoroutineScope.() -> Unit) {
    status
        .map { it == InstallationStatus.Active }
        .distinctUntilChanged()
        .collectLatest { isActive ->
            if (isActive) coroutineScope { block() }
        }
}
