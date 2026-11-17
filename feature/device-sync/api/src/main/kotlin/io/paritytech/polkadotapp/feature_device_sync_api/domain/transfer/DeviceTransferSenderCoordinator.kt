package io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer

import kotlinx.coroutines.flow.StateFlow

interface DeviceTransferSenderCoordinator {
    val state: StateFlow<DeviceTransferState>

    fun confirm(request: DeviceTransferRequest)

    fun reject(request: DeviceTransferRequest)

    fun cancel()
}
