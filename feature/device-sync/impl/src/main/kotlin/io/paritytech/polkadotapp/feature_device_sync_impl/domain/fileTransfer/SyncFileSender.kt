package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncFileMessageScale
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport

internal const val SYNC_FILE_USE_CASE = "device-sync-file"

class SyncFileSender(private val dataTransport: DataTransport) {
    suspend fun send(message: SyncFileMessageScale) {
        dataTransport.send(SYNC_FILE_USE_CASE, BinaryScale.encodeToByteArray<SyncFileMessageScale>(message))
    }

    suspend fun sendCatching(message: SyncFileMessageScale): Result<Unit> = runCancellableCatching { send(message) }
}
