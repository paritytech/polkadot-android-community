package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.utils.InformationSize

enum class DeviceTransferAbortReason {
    USER_CANCELLED,
    INTERNAL_ERROR,
}

sealed interface DeviceTransferSenderOutcome {
    data object Completed : DeviceTransferSenderOutcome

    data class AbortedByReceiver(val reason: DeviceTransferAbortReason) : DeviceTransferSenderOutcome

    data object Cancelled : DeviceTransferSenderOutcome
}

sealed interface DeviceTransferReceiverOutcome {
    data object Completed : DeviceTransferReceiverOutcome

    data class AbortedBySender(val reason: DeviceTransferAbortReason) : DeviceTransferReceiverOutcome

    data object Cancelled : DeviceTransferReceiverOutcome
}

sealed interface DeviceTransferProgress {
    data class Syncing(val appliedChanges: Int, val totalChanges: Int) : DeviceTransferProgress

    data class Files(val receivedBytes: InformationSize, val totalBytes: InformationSize) : DeviceTransferProgress
}
