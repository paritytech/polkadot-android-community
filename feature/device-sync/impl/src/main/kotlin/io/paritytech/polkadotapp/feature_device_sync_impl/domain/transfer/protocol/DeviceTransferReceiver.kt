package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.X25519PrivateKey
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplyRemoteChatMessageUseCase.Placement
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatStateSyncUseCase.SettledIncomingPayment
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds

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
        val RESPONSE_TIMEOUT = 60.seconds
    }

    private val cancelRequest = CompletableDeferred<Unit>()
    private val fileChannels = SyncFileChannelRegistry()
    private val senderMessageArrived = Channel<Unit>(Channel.CONFLATED)
    private val applyingUpdate = MutableStateFlow(false)
    private val mutableProgress = MutableStateFlow<DeviceTransferProgress?>(null)

    val progress: StateFlow<DeviceTransferProgress?> = mutableProgress.asStateFlow()

    @Volatile
    private var waitingPayments = emptyList<SettledIncomingPayment>()

    fun cancel() {
        cancelRequest.complete(Unit)
    }

    suspend fun run(transport: DataTransport): DeviceTransferReceiverOutcome = coroutineScope {
        waitingPayments = emptyList()
        val senderStarted = CompletableDeferred<Unit>()
        val senderCompleted = CompletableDeferred<Unit>()
        val senderAbort = CompletableDeferred<DeviceTransferReceiverOutcome>()

        val updateListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeSyncMessages().collect { message ->
                if (message is SyncMessageScale.Update) {
                    senderStarted.await()
                    applyingUpdate.value = true
                    senderMessageArrived.trySend(Unit)
                    try {
                        apply(transport, message.update)
                    } finally {
                        applyingUpdate.value = false
                    }
                }
            }
        }
        val controlListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeControls().collect { control ->
                senderMessageArrived.trySend(Unit)
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
                dropWaitingPayments()
                pullFiles()
                transport.sendControl(DeviceTransferControlScale.ReceiverCompleted)
                DeviceTransferReceiverOutcome.Completed
            },
            { senderAbort.await() },
            { awaitCancelRequest(transport) },
            { failOnResponseTimeout(senderCompleted) },
        )

        updateListening.cancel()
        controlListening.cancel()
        fileServing.cancel()
        outcome
    }

    private suspend fun apply(transport: DataTransport, update: SyncUpdateScale) {
        if (update.entities.isEmpty()) {
            closeRound(update)
        } else {
            waitingPayments = waitingPayments + applier.apply(update, messagePlacement = Placement.AFTER_EXISTING)
            addAppliedChanges(update.entities.itemCount())
        }

        transport.sendSyncMessage(SyncMessageScale.Ack(SyncUpdateAckScale(update.id)))
    }

    private suspend fun closeRound(update: SyncUpdateScale) {
        if (waitingPayments.isNotEmpty()) waitingPayments = applier.applySettledPayments(waitingPayments)

        if (waitingPayments.isEmpty()) {
            checkpointStorage.saveCheckpoint(senderInstallationId, update.timePoint)
        } else {
            Timber.w("DeviceTransferReceiver: checkpoint held back by ${waitingPayments.size} payment outcomes without a message")
        }
    }

    private fun dropWaitingPayments() {
        if (waitingPayments.isEmpty()) return

        Timber.w("DeviceTransferReceiver: dropped ${waitingPayments.size} payment outcomes without a message")
        waitingPayments = emptyList()
    }

    private fun addAppliedChanges(count: Int) {
        mutableProgress.update { progress ->
            when (progress) {
                is DeviceTransferProgress.Syncing -> progress.copy(appliedChanges = progress.appliedChanges + count)
                is DeviceTransferProgress.Files, null -> progress
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

    private suspend fun failOnResponseTimeout(senderCompleted: Deferred<Unit>): DeviceTransferReceiverOutcome {
        while (true) {
            applyingUpdate.first { applying -> !applying }
            val messageArrived = withTimeoutOrNull(RESPONSE_TIMEOUT) {
                select<Boolean> {
                    senderMessageArrived.onReceive { true }
                    senderCompleted.onAwait { false }
                }
            } ?: throw TimeoutException("No message from the sender for $RESPONSE_TIMEOUT")

            if (!messageArrived) awaitCancellation()
        }
    }

    private suspend fun awaitCancelRequest(transport: DataTransport): DeviceTransferReceiverOutcome {
        cancelRequest.await()
        transport.sendControl(DeviceTransferControlScale.Abort(AbortReasonScale.USER_CANCELLED))
        return DeviceTransferReceiverOutcome.Cancelled
    }
}
