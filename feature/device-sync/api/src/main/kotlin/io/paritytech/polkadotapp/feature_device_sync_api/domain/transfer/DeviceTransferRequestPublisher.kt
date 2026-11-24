package io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer

import java.util.UUID

interface DeviceTransferRequestPublisher {
    suspend fun publishRequest(): Result<DeviceTransferRequest>

    suspend fun cancelRequest(requestId: UUID): Result<Unit>
}
