package io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer

import kotlinx.coroutines.flow.StateFlow

interface DeviceTransferReceiverCoordinator {
    val state: StateFlow<DeviceTransferState>

    fun start()

    fun cancel()
}
