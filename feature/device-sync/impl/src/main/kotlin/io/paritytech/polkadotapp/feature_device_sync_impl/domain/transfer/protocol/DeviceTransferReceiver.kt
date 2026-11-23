package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.X25519PrivateKey
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplyRemoteChatMessageUseCase.Placement
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.AbortReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferControlScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateAckScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferCheckpointStorage
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.SyncEntityApplier
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.SyncFileChannelRegistry
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.SyncFileTransfer
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.raceFirst
import io.paritytech.polkadotapp.feature_statement_store_api.domain.OurDeviceKeypairProvider
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class DeviceTransferReceiver(
    private val senderInstallationId: DataByteArray,
    private val applier: SyncEntityApplier,
    private val checkpointStorage: DeviceTransferCheckpointStorage,
    private val ourDeviceKeypairProvider: OurDeviceKeypairProvider,
    private val fileTransferFactory: SyncFileTransfer.Factory,
    private val filePuller: DeviceTransferFilePuller,
) {
    private companion object {
        const val FILE_PEER_LOG_ID = "device-transfer-sender"
    }

    private val cancelRequest = CompletableDeferred<Unit>()
    private val fileChannels = SyncFileChannelRegistry()
    private val mutableProgress = MutableStateFlow<DeviceTransferProgress>(
        DeviceTransferProgress.Syncing(appliedChanges = 0, totalChanges = 0)
    )

    val progress: StateFlow<DeviceTransferProgress> = mutableProgress.asStateFlow()

    fun cancel() {
        cancelRequest.complete(Unit)
    }

    suspend fun run(transport: DataTransport): DeviceTransferReceiverOutcome = coroutineScope {
        val senderStarted = CompletableDeferred<Unit>()
        val senderCompleted = CompletableDeferred<Unit>()
        val senderAbort = CompletableDeferred<DeviceTransferReceiverOutcome>()

        val updateListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeSyncMessages().collect { message ->
                if (message is SyncMessageScale.Update) {
                    senderStarted.await()
                    apply(transport, message.update)
                }
            }
        }
        val controlListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeControls().collect { control ->
                handleControl(control, senderStarted, senderCompleted, senderAbort)
            }
        }
        val fileServing = launch(start = CoroutineStart.UNDISPATCHED) {
            fileTransferFactory.createForDeviceTransfer(transport, FILE_PEER_LOG_ID, fileChannels).run()
        }

        transport.awaitOpen()

        val outcome = raceFirst(
            {
                transport.sendControl(DeviceTransferControlScale.Resume(checkpointStorage.getCheckpoint(senderInstallationId)))
                senderCompleted.await()
                pullFiles()
                transport.sendControl(DeviceTransferControlScale.ReceiverCompleted)
                DeviceTransferReceiverOutcome.Completed
            },
            { senderAbort.await() },
            { awaitCancelRequest(transport) },
        )

        updateListening.cancel()
        controlListening.cancel()
        fileServing.cancel()
        outcome
    }

    private suspend fun apply(transport: DataTransport, update: SyncUpdateScale) {
        if (update.entities.isEmpty()) {
            checkpointStorage.saveCheckpoint(senderInstallationId, update.timePoint)
        } else {
            applier.apply(update, messagePlacement = Placement.AFTER_EXISTING)
            addAppliedChanges(update.entities.itemCount())
        }

        transport.sendSyncMessage(SyncMessageScale.Ack(SyncUpdateAckScale(update.id)))
    }

    private fun addAppliedChanges(count: Int) {
        mutableProgress.update { progress ->
            when (progress) {
                is DeviceTransferProgress.Syncing -> progress.copy(appliedChanges = progress.appliedChanges + count)
                is DeviceTransferProgress.Files -> progress
            }
        }
    }

    private suspend fun handleControl(
        control: DeviceTransferControlScale,
        senderStarted: CompletableDeferred<Unit>,
        senderCompleted: CompletableDeferred<Unit>,
        senderAbort: CompletableDeferred<DeviceTransferReceiverOutcome>,
    ) {
        when (control) {
            is DeviceTransferControlScale.SenderStarted -> {
                ourDeviceKeypairProvider.importPrivateKey(X25519PrivateKey.fromDerivedBytes(control.deviceEncryptionKey))
                mutableProgress.value = DeviceTransferProgress.Syncing(appliedChanges = 0, totalChanges = control.totalChanges.toInt())
                senderStarted.complete(Unit)
            }

            DeviceTransferControlScale.SenderCompleted -> senderCompleted.complete(Unit)

            is DeviceTransferControlScale.Abort ->
                senderAbort.complete(DeviceTransferReceiverOutcome.AbortedBySender(control.reason.toDomain()))

            DeviceTransferControlScale.ReceiverCompleted, is DeviceTransferControlScale.Resume -> Unit
        }
    }

    private suspend fun pullFiles() {
        val requester = fileChannels.openRequester()
        if (requester == null) {
            Timber.w("DeviceTransferReceiver: no file channel, completing without files")
            return
        }

        filePuller.pull(requester, onProgress = { mutableProgress.value = it })
            .logFailure("DeviceTransferReceiver: failed to pull missing files, completing without them")
    }

    private suspend fun awaitCancelRequest(transport: DataTransport): DeviceTransferReceiverOutcome {
        cancelRequest.await()
        transport.sendControl(DeviceTransferControlScale.Abort(AbortReasonScale.USER_CANCELLED))
        return DeviceTransferReceiverOutcome.Cancelled
    }
}
