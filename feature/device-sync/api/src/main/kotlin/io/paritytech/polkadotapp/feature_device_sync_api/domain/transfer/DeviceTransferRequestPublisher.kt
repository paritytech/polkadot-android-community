package io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer

interface DeviceTransferRequestPublisher {
    suspend fun publishRequest(): Result<DeviceTransferRequest>

    suspend fun cancelRequest(requestId: String): Result<Unit>
}
