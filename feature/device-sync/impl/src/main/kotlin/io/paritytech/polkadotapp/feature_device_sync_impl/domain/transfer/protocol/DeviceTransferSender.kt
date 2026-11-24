package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.AbortReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferControlScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.raceFirst
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class DeviceTransferSender(
    private val updates: List<SyncUpdateScale>,
    private val deviceKey: DataByteArray,
) {
    private val acknowledgedUpdateIds = mutableSetOf<UInt>()
    private val cancelRequest = CompletableDeferred<Unit>()

    val totalChanges: Int = updates.sumOf { it.entities.itemCount() }

    fun cancel() {
        cancelRequest.complete(Unit)
    }

    suspend fun run(transport: DataTransport): DeviceTransferSenderOutcome = coroutineScope {
        val acks = Channel<UInt>(Channel.UNLIMITED)
        val receiverEnd = CompletableDeferred<DeviceTransferSenderOutcome>()

        val ackListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeSyncMessages().collect { message ->
                if (message is SyncMessageScale.Ack) acks.send(message.ack.id)
            }
        }
        val controlListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeControls().collect { control ->
                control.toSenderOutcome()?.let(receiverEnd::complete)
            }
        }

        val outcome = raceFirst(
            {
                sendSnapshot(transport, acks)
                awaitCancellation()
            },
            { receiverEnd.await() },
            { awaitCancelRequest(transport) },
        )

        ackListening.cancel()
        controlListening.cancel()
        outcome
    }

    private suspend fun sendSnapshot(transport: DataTransport, acks: ReceiveChannel<UInt>) {
        transport.sendControl(DeviceTransferControlScale.Begin(totalChanges.toUInt()))

        for (update in updates) {
            if (update.id in acknowledgedUpdateIds) continue

            transport.sendSyncMessage(SyncMessageScale.Update(update))
            awaitAck(update.id, acks)
            acknowledgedUpdateIds += update.id
        }

        transport.sendControl(DeviceTransferControlScale.SenderCompleted(deviceKey.value))
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
        is DeviceTransferControlScale.Begin, is DeviceTransferControlScale.SenderCompleted -> null
    }
}
