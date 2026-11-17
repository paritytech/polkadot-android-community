package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferSessionMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling.SyncSignalingEnvelope
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling.SyncSignalingTransport
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling.subscribeNewMessages
import io.paritytech.polkadotapp.feature_statement_store_api.domain.CommunicationSession
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.CommunicationSessionEvent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber

class DeviceTransferSessionTransport(
    private val communicationSession: CommunicationSession,
    scope: CoroutineScope,
) : SyncSignalingTransport {
    private val envelopes = Channel<SyncSignalingEnvelope>(Channel.UNLIMITED)
    private val rejection = CompletableDeferred<Unit>()

    init {
        communicationSession.subscribeNewMessages()
            .mapNotNull(::decode)
            .onEach(::route)
            .launchIn(scope)
    }

    override fun send(envelope: SyncSignalingEnvelope) {
        send(DeviceTransferSessionMessageScale.Signaling(envelope))
    }

    override fun subscribeEnvelopes(): Flow<SyncSignalingEnvelope> = envelopes.receiveAsFlow()

    suspend fun sendRejection(): Result<Unit> = coroutineScope {
        val rejectionMessage = encode(DeviceTransferSessionMessageScale.Rejected)
        val sendOutcome = async(start = CoroutineStart.UNDISPATCHED) {
            communicationSession.subscribeEvents().mapNotNull { event -> event.sendOutcomeOf(rejectionMessage) }.first()
        }

        communicationSession.sendMessage(rejectionMessage)
        sendOutcome.await()
    }

    suspend fun awaitRejection() {
        rejection.await()
    }

    private fun send(message: DeviceTransferSessionMessageScale) {
        communicationSession.sendMessage(encode(message))
    }

    private fun encode(message: DeviceTransferSessionMessageScale): EncodedMessage {
        return BinaryScale.encodeToByteArray<DeviceTransferSessionMessageScale>(message)
    }

    private fun CommunicationSessionEvent.sendOutcomeOf(message: EncodedMessage): Result<Unit>? = when (this) {
        is CommunicationSessionEvent.MessagesSentSuccessfully -> Result.success(Unit).takeIf { messages.any { it.contentEquals(message) } }
        is CommunicationSessionEvent.MessagesFailedToSend -> Result.failure<Unit>(error).takeIf { messages.any { it.contentEquals(message) } }
        is CommunicationSessionEvent.SessionFailed -> Result.failure(error)
        else -> null
    }

    private fun route(message: DeviceTransferSessionMessageScale) {
        when (message) {
            DeviceTransferSessionMessageScale.Rejected -> rejection.complete(Unit)
            is DeviceTransferSessionMessageScale.Signaling -> envelopes.trySend(message.envelope)
        }
    }

    private fun decode(encoded: ByteArray): DeviceTransferSessionMessageScale? =
        BinaryScale.decodeFromByteArrayCatching<DeviceTransferSessionMessageScale>(encoded)
            .onFailure { Timber.w(it, "Failed to decode a device transfer session message (${encoded.size}b)") }
            .getOrNull()
}
