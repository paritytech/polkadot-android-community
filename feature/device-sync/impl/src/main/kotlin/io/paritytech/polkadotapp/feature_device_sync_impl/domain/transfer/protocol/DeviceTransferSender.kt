package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.AbortReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferControlScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.raceFirst
import io.paritytech.polkadotapp.feature_statement_store_api.domain.OurDeviceKeypairProvider
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class DeviceTransferSender(
    private val collectUpdates: suspend (checkpoint: ULong?) -> List<SyncUpdateScale>,
    private val ourDeviceKeypairProvider: OurDeviceKeypairProvider,
) {
    private val cancelRequest = CompletableDeferred<Unit>()

    fun cancel() {
        cancelRequest.complete(Unit)
    }

    suspend fun run(transport: DataTransport): DeviceTransferSenderOutcome = coroutineScope {
        val acks = Channel<UInt>(Channel.UNLIMITED)
        val resumeCheckpoints = Channel<ULong?>(Channel.UNLIMITED)
        val receiverEnd = CompletableDeferred<DeviceTransferSenderOutcome>()

        val ackListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeSyncMessages().collect { message ->
                if (message is SyncMessageScale.Ack) acks.send(message.ack.id)
            }
        }
        val controlListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeControls().collect { control ->
                if (control is DeviceTransferControlScale.Resume) resumeCheckpoints.send(control.fromTimePoint)
                control.toSenderOutcome()?.let(receiverEnd::complete)
            }
        }

        val outcome = raceFirst(
            {
                val updates = collectUpdates(resumeCheckpoints.receive())
                sendUpdates(transport, updates, acks)
                awaitCancellation()
            },
            { receiverEnd.await() },
            { awaitCancelRequest(transport) },
        )

        ackListening.cancel()
        controlListening.cancel()
        outcome
    }

    private suspend fun sendUpdates(
        transport: DataTransport,
        updates: List<SyncUpdateScale>,
        acks: ReceiveChannel<UInt>,
    ) {
        val deviceKey = ourDeviceKeypairProvider.get().privateKey.bytes.value
        val totalChanges = updates.sumOf { it.entities.itemCount() }
        transport.sendControl(DeviceTransferControlScale.SenderStarted(deviceKey, totalChanges.toUInt()))

        for (update in updates) {
            transport.sendSyncMessage(SyncMessageScale.Update(update))
            awaitAck(update.id, acks)
        }

        transport.sendControl(DeviceTransferControlScale.SenderCompleted)
    }

    private suspend fun awaitAck(updateId: UInt, acks: ReceiveChannel<UInt>) {
        do {
            val ackedId = acks.receive()
        } while (ackedId != updateId)
    }

    private suspend fun awaitCancelRequest(transport: DataTransport): DeviceTransferSenderOutcome {
        cancelRequest.await()
        transport.sendControl(DeviceTransferControlScale.Abort(AbortReasonScale.USER_CANCELLED))
        return DeviceTransferSenderOutcome.Cancelled
    }

    private fun DeviceTransferControlScale.toSenderOutcome(): DeviceTransferSenderOutcome? = when (this) {
        DeviceTransferControlScale.ReceiverCompleted -> DeviceTransferSenderOutcome.Completed
        is DeviceTransferControlScale.Abort -> DeviceTransferSenderOutcome.AbortedByReceiver(reason.toDomain())
        is DeviceTransferControlScale.SenderStarted,
        DeviceTransferControlScale.SenderCompleted,
        is DeviceTransferControlScale.Resume -> null
    }
}
