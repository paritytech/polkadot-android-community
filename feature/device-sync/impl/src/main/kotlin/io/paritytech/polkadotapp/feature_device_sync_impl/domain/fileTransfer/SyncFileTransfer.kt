package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncFileMessageScale
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val SYNC_FILE_USE_CASE = "device-sync-file"

/**
 * `device-sync-file` on one open data channel: serves the sibling's requests and offers this side's requester
 * through [SyncFileChannelRegistry] while the channel is open. Request ids are scoped to this lifetime.
 */
class SyncFileTransfer(
    private val dataTransport: DataTransport,
    private val locator: SyncFileLocator,
    private val registry: SyncFileChannelRegistry,
    private val peerLogId: String,
    private val noResponseTimeout: Duration,
) {
    class Factory @Inject constructor(
        private val locator: SyncFileLocator,
        private val registry: SyncFileChannelRegistry,
    ) {
        private companion object {
            val NO_RESPONSE_TIMEOUT = 30.seconds
        }

        fun create(dataTransport: DataTransport, peerLogId: String): SyncFileTransfer {
            return SyncFileTransfer(dataTransport, locator, registry, peerLogId, NO_RESPONSE_TIMEOUT)
        }
    }

    /** Runs until cancelled together with the channel. */
    suspend fun run(): Unit = coroutineScope {
        val sender = SyncFileSender(dataTransport)
        val provider = SyncFileProvider(sender, locator, peerLogId, scope = this)
        val requester = SyncFileRequester(sender, peerLogId, noResponseTimeout, scope = this)
        val registration = registry.register(requester)

        try {
            dataTransport.subscribeMessages(SYNC_FILE_USE_CASE)
                .mapNotNull(::decode)
                .collect { route(it, provider, requester) }
        } finally {
            registry.unregister(registration)
            withContext(NonCancellable) { requester.close() }
        }
    }

    private suspend fun route(message: SyncFileMessageScale, provider: SyncFileProvider, requester: SyncFileRequester) {
        when (message) {
            is SyncFileMessageScale.FilesRequest -> provider.handle(message.request)
            is SyncFileMessageScale.CancelFilesRequest -> provider.cancel(message.requestId)
            is SyncFileMessageScale.AvailabilityRequest -> provider.handle(message.request)

            is SyncFileMessageScale.Header,
            is SyncFileMessageScale.Chunk,
            is SyncFileMessageScale.Unavailable,
            is SyncFileMessageScale.Completed -> requester.handle(message)

            is SyncFileMessageScale.AvailabilityResponse ->
                Timber.d("SyncFileTransfer: ignoring unrequested availability response from $peerLogId")
        }
    }

    private fun decode(payload: ByteArray): SyncFileMessageScale? {
        return BinaryScale.decodeFromByteArrayCatching<SyncFileMessageScale>(payload)
            .onFailure { Timber.w(it, "SyncFileTransfer: failed to decode message from $peerLogId") }
            .getOrNull()
    }
}

class SyncFileSender(private val dataTransport: DataTransport) {
    suspend fun send(message: SyncFileMessageScale) {
        dataTransport.send(SYNC_FILE_USE_CASE, BinaryScale.encodeToByteArray<SyncFileMessageScale>(message))
    }

    suspend fun sendCatching(message: SyncFileMessageScale): Result<Unit> = runCancellableCatching { send(message) }
}
