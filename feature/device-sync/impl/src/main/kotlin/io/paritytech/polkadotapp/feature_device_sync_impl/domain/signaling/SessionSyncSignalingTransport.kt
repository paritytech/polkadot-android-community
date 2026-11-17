package io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.feature_statement_store_api.domain.CommunicationSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber

class SessionSyncSignalingTransport(
    private val communicationSession: CommunicationSession,
) : SyncSignalingTransport {
    override fun send(envelope: SyncSignalingEnvelope) {
        communicationSession.sendMessage(BinaryScale.encodeToByteArray(SyncSignalingEnvelope.serializer(), envelope))
    }

    override fun subscribeEnvelopes(): Flow<SyncSignalingEnvelope> {
        return communicationSession.subscribeNewMessages().mapNotNull(::decode)
    }

    private fun decode(encoded: ByteArray): SyncSignalingEnvelope? =
        BinaryScale.decodeFromByteArrayCatching<SyncSignalingEnvelope>(encoded)
            .onFailure { Timber.w(it, "SyncSignaling: failed to decode envelope (${encoded.size}b)") }
            .onSuccess { Timber.d("SyncSignaling: decoded: ${it.message::class.simpleName} ${it.offerId} ") }
            .getOrNull()
}
