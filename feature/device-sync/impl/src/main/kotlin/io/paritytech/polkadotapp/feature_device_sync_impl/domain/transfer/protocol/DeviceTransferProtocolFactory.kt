package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferCheckpointStorage
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.SyncEntityApplier
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.SyncFileTransfer
import io.paritytech.polkadotapp.feature_statement_store_api.domain.OurDeviceKeypairProvider
import javax.inject.Inject

class DeviceTransferProtocolFactory @Inject constructor(
    private val sequencer: DeviceTransferSenderSequencer,
    private val applier: SyncEntityApplier,
    private val ourDeviceKeypairProvider: OurDeviceKeypairProvider,
    private val checkpointStorage: DeviceTransferCheckpointStorage,
    private val fileTransferFactory: SyncFileTransfer.Factory,
    private val filePuller: DeviceTransferFilePuller,
) {
    fun createSender(): DeviceTransferSender = DeviceTransferSender(sequencer, ourDeviceKeypairProvider, fileTransferFactory)

    fun createReceiver(senderInstallationId: DataByteArray): DeviceTransferReceiver {
        return DeviceTransferReceiver(
            senderInstallationId,
            applier,
            checkpointStorage,
            ourDeviceKeypairProvider,
            fileTransferFactory,
            filePuller,
        )
    }
}
