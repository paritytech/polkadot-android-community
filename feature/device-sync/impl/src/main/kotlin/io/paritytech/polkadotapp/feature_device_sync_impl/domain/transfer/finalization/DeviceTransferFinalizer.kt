package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.finalization

import io.paritytech.polkadotapp.common.utils.exponentialRetryDelay
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallationManager
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferCheckpointStorage
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferProcessedRequestStorage
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DeviceTransferFinalizer @Inject constructor(
    private val activeInstallationManager: ActiveInstallationManager,
    private val processedRequestStorage: DeviceTransferProcessedRequestStorage,
    private val checkpointStorage: DeviceTransferCheckpointStorage,
) {
    private companion object {
        const val PUBLISH_ATTEMPTS = 10
        val PUBLISH_RETRY_DELAY = exponentialRetryDelay(initialDelay = 1.seconds, maxDelay = 5.minutes)
    }

    suspend fun finalize(ownRequestId: UUID): Result<Unit> {
        processedRequestStorage.markProcessed(ownRequestId)

        return publishOwnRecord()
            .onSuccess { checkpointStorage.clear() }
    }

    private suspend fun publishOwnRecord(): Result<Unit> {
        var attempt = 1
        var result = activeInstallationManager.activateThisInstallation()

        while (result.isFailure && attempt < PUBLISH_ATTEMPTS) {
            result.logFailure("Device transfer: failed to publish the active installation record, attempt $attempt")
            delay(PUBLISH_RETRY_DELAY.delayFor(attempt))
            attempt++
            result = activeInstallationManager.activateThisInstallation()
        }

        return result
    }
}
