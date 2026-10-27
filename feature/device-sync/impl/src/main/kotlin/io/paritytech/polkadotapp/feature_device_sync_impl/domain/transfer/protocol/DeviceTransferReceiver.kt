package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.AbortReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferControlScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateAckScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.raceFirst
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceTransferReceiver(
    private val applyUpdate: suspend (SyncUpdateScale) -> Unit,
) {
    private val appliedUpdateIds = mutableSetOf<UInt>()
    private val cancelRequest = CompletableDeferred<Unit>()
    private val mutableProgress = MutableStateFlow(DeviceTransferProgress(appliedChanges = 0, totalChanges = 0))

    val progress: StateFlow<DeviceTransferProgress> = mutableProgress.asStateFlow()

    fun cancel() {
        cancelRequest.complete(Unit)
    }

    suspend fun run(transport: DataTransport): DeviceTransferReceiverOutcome = coroutineScope {
        val senderEnd = CompletableDeferred<DeviceTransferReceiverOutcome>()

        val updateListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeSyncMessages().collect { message ->
                if (message is SyncMessageScale.Update) apply(transport, message.update)
            }
        }
        val controlListening = launch(start = CoroutineStart.UNDISPATCHED) {
            transport.subscribeControls().collect { control ->
                handleControl(transport, control)?.let(senderEnd::complete)
            }
        }

        val outcome = raceFirst(
            { senderEnd.await() },
            { awaitCancelRequest(transport) },
        )

        updateListening.cancel()
        controlListening.cancel()
        outcome
    }

    private suspend fun apply(transport: DataTransport, update: SyncUpdateScale) {
        if (update.id !in appliedUpdateIds) {
            applyUpdate(update)
            appliedUpdateIds += update.id
            mutableProgress.update { it.copy(appliedChanges = it.appliedChanges + update.entities.itemCount()) }
        }

        transport.sendSyncMessage(SyncMessageScale.Ack(SyncUpdateAckScale(update.id)))
    }

    private suspend fun handleControl(
        transport: DataTransport,
        control: DeviceTransferControlScale,
    ): DeviceTransferReceiverOutcome? = when (control) {
        is DeviceTransferControlScale.Begin -> {
            mutableProgress.update { it.copy(totalChanges = control.totalChanges.toInt()) }
            null
        }

        is DeviceTransferControlScale.SenderCompleted -> {
            transport.sendControl(DeviceTransferControlScale.ReceiverCompleted)
            DeviceTransferReceiverOutcome.Completed(control.deviceKey.toDataByteArray())
        }

        is DeviceTransferControlScale.Abort -> DeviceTransferReceiverOutcome.AbortedBySender(control.reason.toDomain())

        DeviceTransferControlScale.ReceiverCompleted -> null
    }

    private suspend fun awaitCancelRequest(transport: DataTransport): DeviceTransferReceiverOutcome {
        cancelRequest.await()
        transport.sendControl(DeviceTransferControlScale.Abort(AbortReasonScale.USER_CANCELLED))
        return DeviceTransferReceiverOutcome.Cancelled
    }
}
