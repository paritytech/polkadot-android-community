package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.AbortReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferControlScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.SYNC_FILE_USE_CASE
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.SyncFileChannelRegistry
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.SyncFileTransfer
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.raceFirst
import io.paritytech.polkadotapp.feature_statement_store_api.domain.OurDeviceKeypairProvider
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DeviceTransferSender(
    private val sequencer: DeviceTransferSenderSequencer,
    private val ourDeviceKeypairProvider: OurDeviceKeypairProvider,
    private val fileTransferFactory: SyncFileTransfer.Factory,
) {
    private companion object {
        const val FILE_PEER_LOG_ID = "device-transfer-receiver"
        val RESPONSE_TIMEOUT = 30.seconds
        val NO_FILE_REQUEST_TIMEOUT = 15.minutes
    }

    private val cancelRequest = CompletableDeferred<Unit>()
    private val fileChannels = SyncFileChannelRegistry()

    fun cancel() {
        cancelRequest.complete(Unit)
    }

    suspend fun run(transport: DataTransport): DeviceTransferSenderOutcome = coroutineScope {
        val acks = Channel<UInt>(Channel.UNLIMITED)
        val resumes = Channel<DeviceTransferControlScale.Resume>(Channel.UNLIMITED)
        val fileRequests = Channel<Unit>(Channel.CONFLATED)
        val receiverEnd = CompletableDeferred<DeviceTransferSenderOutcome>()

        val ackListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeSyncMessages().collect { message ->
                if (message is SyncMessageScale.Ack) acks.send(message.ack.id)
            }
        }
        val controlListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeControls().collect { control ->
                if (control is DeviceTransferControlScale.Resume) resumes.send(control)
                control.toSenderOutcome()?.let(receiverEnd::complete)
            }
        }
        val fileRequestListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeMessages(SYNC_FILE_USE_CASE).collect { fileRequests.trySend(Unit) }
        }
        val fileServing = launch(start = CoroutineStart.UNDISPATCHED) {
            fileTransferFactory.createForDeviceTransfer(transport, FILE_PEER_LOG_ID, fileChannels).run()
        }

        transport.awaitOpen()

        val outcome = raceFirst(
            {
                val resume = receiveWithinResponseTimeout(resumes)
                sendSteps(transport, sequencer.stepsFrom(resume.fromTimePoint), acks)
                failWhenNoFileRequests(fileRequests)
            },
            { receiverEnd.await() },
            { awaitCancelRequest(transport) },
        )

        ackListening.cancel()
        controlListening.cancel()
        fileRequestListening.cancel()
        fileServing.cancel()
        outcome
    }

    private suspend fun sendSteps(
        transport: DataTransport,
        steps: Flow<DeviceTransferSenderStep>,
        acks: ReceiveChannel<UInt>,
    ) {
        steps.collect { step ->
            when (step) {
                is DeviceTransferSenderStep.Started -> {
                    val deviceKey = ourDeviceKeypairProvider.get().privateKey.bytes.value
                    transport.sendControl(DeviceTransferControlScale.SenderStarted(deviceKey, step.totalChanges.toUInt()))
                }

                is DeviceTransferSenderStep.Update -> {
                    transport.sendSyncMessage(SyncMessageScale.Update(step.update))
                    awaitAck(step.update.id, acks)
                }
            }
        }

        transport.sendControl(DeviceTransferControlScale.SenderCompleted)
    }

    private suspend fun awaitAck(updateId: UInt, acks: ReceiveChannel<UInt>) {
        do {
            val ackedId = receiveWithinResponseTimeout(acks)
        } while (ackedId != updateId)
    }

    private suspend fun <T : Any> receiveWithinResponseTimeout(channel: ReceiveChannel<T>): T {
        return withTimeoutOrNull(RESPONSE_TIMEOUT) { channel.receive() }
            ?: throw TimeoutException("No response from the receiver for $RESPONSE_TIMEOUT")
    }

    private suspend fun failWhenNoFileRequests(fileRequests: ReceiveChannel<Unit>): Nothing {
        while (true) {
            withTimeoutOrNull(NO_FILE_REQUEST_TIMEOUT) { fileRequests.receive() }
                ?: throw TimeoutException("No file request from the receiver for $NO_FILE_REQUEST_TIMEOUT")
        }
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
