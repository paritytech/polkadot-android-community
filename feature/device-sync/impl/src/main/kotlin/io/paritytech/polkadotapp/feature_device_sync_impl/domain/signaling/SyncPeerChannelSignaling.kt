package io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import io.paritytech.polkadotapp.feature_statement_store_api.domain.CommunicationSession
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.CommunicationSessionEvent
import io.paritytech.polkadotapp.tools_media_connection_api.domain.EncodedIceCandidates
import io.paritytech.polkadotapp.tools_media_connection_api.domain.EncodedSdp
import io.paritytech.polkadotapp.tools_media_connection_api.domain.PeerChannelSignaling
import io.paritytech.polkadotapp.tools_media_connection_api.domain.signaling.SignalingMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC signalling for one own-device peer over [communicationSession]. Every message is wrapped in
 * a [SyncSignalingEnvelope] carrying a [SyncOfferId] (UUID per connection attempt): the initiator
 * mints it on [sendOffer], the acceptor adopts it from the incoming Offer. Answer/IceCandidates are
 * filtered by the active offerId (stale ones dropped); Offer/Reconnected are not — the latest in a
 * batch wins. [onOfferIdDetermined] persists the id for restart-recovery (acceptor on receiving the
 * Offer; initiator once the Answer arrives). Undecodable payloads are logged and dropped.
 */
class SyncPeerChannelSignaling(
    private val communicationSession: CommunicationSession,
    private val onOfferIdDetermined: suspend (SyncOfferId) -> Unit,
) : PeerChannelSignaling {
    private val activeOfferFlow = MutableStateFlow<SyncOfferId?>(null)
    private val respondedRequestIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override suspend fun sendOffer(sdp: EncodedSdp) {
        val offerId = UUID.randomUUID().toString()
        activeOfferFlow.value = offerId
        sendMessage(offerId, SignalingMessage.Offer(sdp))
    }

    override suspend fun sendAnswer(sdp: EncodedSdp) {
        sendMessage(awaitOfferId(), SignalingMessage.Answer(sdp))
    }

    override suspend fun sendIceCandidates(candidates: EncodedIceCandidates) {
        sendMessage(awaitOfferId(), SignalingMessage.IceCandidates(candidates))
    }

    override fun subscribeIncomingIceCandidates(): Flow<EncodedIceCandidates> =
        incomingIceCandidates().map { it.candidates }

    override suspend fun awaitIncomingAnswer(): EncodedSdp {
        val answer = incomingAnswers().first()
        onOfferIdDetermined(awaitOfferId())
        return answer.sdp
    }

    override suspend fun awaitIncomingOffer(): EncodedSdp {
        val (offerId, offer) = incomingOffers().first()
        activeOfferFlow.value = offerId
        onOfferIdDetermined(offerId)
        return offer.sdp
    }

    fun sendReconnected(offerId: SyncOfferId) {
        sendMessage(offerId, SignalingMessage.Reconnected)
    }

    fun subscribeReconnected(): Flow<SyncOfferId> = incomingReconnects()

    /** Suspends until the peer asks to drop our current attempt (a Reconnected matching the active offerId). */
    suspend fun awaitReconnectRequest() {
        subscribeReconnected().first { it == activeOfferId() }
    }

    fun activeOfferId(): SyncOfferId? = activeOfferFlow.value

    private fun sendMessage(offerId: SyncOfferId, message: SignalingMessage) {
        val envelope = SyncSignalingEnvelope(offerId, message)
        val encoded = BinaryScale.encodeToByteArray(SyncSignalingEnvelope.serializer(), envelope)
        Timber.tag("signal_log").d("SyncSignaling: sending ${message::class.simpleName} offerId=$offerId (${encoded.size}b)")
        communicationSession.sendMessage(encoded)
    }

    // Latest Offer in each batch wins (acceptor adopts the newest); not filtered by offerId.
    private fun incomingOffers(): Flow<Pair<SyncOfferId, SignalingMessage.Offer>> =
        lastInBatchOfType<SignalingMessage.Offer>().map { it.offerId to it.message as SignalingMessage.Offer }

    // Latest Reconnected in each batch; not filtered by offerId (it references the attempt to dispose).
    private fun incomingReconnects(): Flow<SyncOfferId> =
        lastInBatchOfType<SignalingMessage.Reconnected>().map { it.offerId }

    private fun incomingAnswers(): Flow<SignalingMessage.Answer> = envelopesForActiveOffer()

    private fun incomingIceCandidates(): Flow<SignalingMessage.IceCandidates> = envelopesForActiveOffer()

    private inline fun <reified T : SignalingMessage> lastInBatchOfType(): Flow<SyncSignalingEnvelope> =
        incomingEnvelopeBatches().mapNotNull { batch -> batch.lastOrNull { it.message is T } }

    private inline fun <reified T : SignalingMessage> envelopesForActiveOffer(): Flow<T> =
        incomingEnvelopeBatches().transform { batch ->
            batch.forEach { envelope ->
                if (envelope.offerId == awaitOfferId() && envelope.message is T) {
                    emit(envelope.message)
                }
            }
        }

    private fun incomingEnvelopeBatches(): Flow<List<SyncSignalingEnvelope>> =
        communicationSession.subscribeEvents()
            .filterIsInstance<CommunicationSessionEvent.NewMessagesReceived>()
            .onEach { event ->
                if (respondedRequestIds.add(event.requestId)) {
                    communicationSession.respond(event.requestId, SYNC_RESPONSE_SUCCESS)
                }
            }
            .mapNotNull { event ->
                val envelopes = event.messages.mapNotNull { encodedMessage ->
                    BinaryScale.decodeFromByteArrayCatching<SyncSignalingEnvelope>(encodedMessage)
                        .onFailure { Timber.w(it, "SyncSignaling: failed to decode envelope (${encodedMessage.size}b)") }
                        .onSuccess { Timber.d("SyncSignaling: decoded ${it::class.simpleName}") }
                        .getOrNull()
                }

                envelopes.ifEmpty { null }
            }

    private suspend fun awaitOfferId(): SyncOfferId = activeOfferFlow.filterNotNull().first()

    private companion object {
        private val SYNC_RESPONSE_SUCCESS: UByte = 0u
    }
}
