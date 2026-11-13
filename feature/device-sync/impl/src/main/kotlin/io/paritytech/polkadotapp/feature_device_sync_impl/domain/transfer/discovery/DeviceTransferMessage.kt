package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.discovery

import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequest

sealed interface DeviceTransferMessage {
    data class Request(val request: DeviceTransferRequest) : DeviceTransferMessage

    data class Cancel(val requestId: String) : DeviceTransferMessage
}
