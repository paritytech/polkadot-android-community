package io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer

import kotlinx.coroutines.flow.Flow

interface DeviceTransferRequestListener {
    fun observePendingRequest(): Flow<DeviceTransferRequest?>

    suspend fun markProcessed(requestId: String)
}
