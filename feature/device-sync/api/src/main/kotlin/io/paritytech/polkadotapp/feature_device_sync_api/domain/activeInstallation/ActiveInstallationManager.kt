package io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation

import kotlinx.coroutines.flow.StateFlow

/**
 * Follows the account's active installation record (Device Transfer RFC §8): only one installation of an account
 * is active at a time.
 */
interface ActiveInstallationManager {
    val status: StateFlow<InstallationStatus>

    /**
     * Publishes this installation as the account's active one: after a transfer to it, or when the user activates
     * it without a transfer.
     */
    suspend fun activateThisInstallation(): Result<Unit>
}
