package io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface DeviceTransferRequestListener {
    fun observePendingRequest(): Flow<DeviceTransferRequest?>

    suspend fun markProcessed(requestId: UUID)
}
