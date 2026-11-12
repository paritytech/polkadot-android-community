package io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer

import io.paritytech.polkadotapp.common.utils.InformationSize

sealed interface DeviceTransferState {
    data object Idle : DeviceTransferState

    data object WaitingForSender : DeviceTransferState

    data object Connecting : DeviceTransferState

    data class Syncing(val appliedChanges: Int, val totalChanges: Int) : DeviceTransferState

    data class Files(val receivedBytes: InformationSize, val totalBytes: InformationSize) : DeviceTransferState

    data object Finalizing : DeviceTransferState

    data object Completed : DeviceTransferState

    data class Failed(val reason: DeviceTransferFailure) : DeviceTransferState
}

enum class DeviceTransferFailure {
    REJECTED,
    CANCELLED,
    ABORTED,
    TIMED_OUT,
    FAILED,
}
