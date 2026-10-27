package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.AbortReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferControlScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncMessageScale
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber

private const val DEVICE_SYNC_USE_CASE = "device-sync"
private const val DEVICE_TRANSFER_USE_CASE = "deviceTransfer"

suspend fun DataTransport.sendSyncMessage(message: SyncMessageScale) {
    send(DEVICE_SYNC_USE_CASE, BinaryScale.encodeToByteArray<SyncMessageScale>(message))
}

suspend fun DataTransport.sendControl(control: DeviceTransferControlScale) {
    send(DEVICE_TRANSFER_USE_CASE, BinaryScale.encodeToByteArray<DeviceTransferControlScale>(control))
}

fun DataTransport.subscribeSyncMessages(): Flow<SyncMessageScale> {
    return subscribeMessages(DEVICE_SYNC_USE_CASE).mapNotNull { payload ->
        BinaryScale.decodeFromByteArrayCatching<SyncMessageScale>(payload)
            .onFailure { Timber.w(it, "Device transfer: failed to decode a sync message") }
            .getOrNull()
    }
}

fun DataTransport.subscribeControls(): Flow<DeviceTransferControlScale> {
    return subscribeMessages(DEVICE_TRANSFER_USE_CASE).mapNotNull { payload ->
        BinaryScale.decodeFromByteArrayCatching<DeviceTransferControlScale>(payload)
            .onFailure { Timber.w(it, "Device transfer: failed to decode a control message") }
            .getOrNull()
    }
}

fun List<SyncEntityScale>.itemCount(): Int = sumOf { entity ->
    when (entity) {
        is SyncEntityScale.Devices -> entity.devices.size
        is SyncEntityScale.ChatsAdded -> entity.chats.size
        is SyncEntityScale.ChatsRemoved -> entity.chats.size
        is SyncEntityScale.Messages -> entity.messages.size
    }
}

fun AbortReasonScale.toDomain(): DeviceTransferAbortReason = when (this) {
    AbortReasonScale.USER_CANCELLED -> DeviceTransferAbortReason.USER_CANCELLED
    AbortReasonScale.INTERNAL_ERROR -> DeviceTransferAbortReason.INTERNAL_ERROR
}
