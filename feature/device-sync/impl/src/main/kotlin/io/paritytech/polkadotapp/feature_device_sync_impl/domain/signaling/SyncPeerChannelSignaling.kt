package io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling

import io.paritytech.polkadotapp.tools_media_connection_api.domain.EncodedIceCandidates
import io.paritytech.polkadotapp.tools_media_connection_api.domain.EncodedSdp
import io.paritytech.polkadotapp.tools_media_connection_api.domain.PeerChannelSignaling
import io.paritytech.polkadotapp.tools_media_connection_api.domain.signaling.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import java.util.UUID

/**
 * WebRTC signalling for one own-device peer over [transport]. Every message is wrapped in
 * a [SyncSignalingEnvelope] carrying a [SyncOfferId] (UUID per connection attempt): the initiator
 * mints it on [sendOffer], the acceptor adopts it from the incoming Offer. [onOfferIdDetermined]
 * persists the id for restart-recovery (acceptor on receiving the Offer; initiator once the Answer
 * arrives).
 */
class SyncPeerChannelSignaling(
    private val transport: SyncSignalingTransport,
    scope: CoroutineScope,
    private val onOfferIdDetermined: suspend (SyncOfferId) -> Unit,
) : PeerChannelSignaling {
    private val activeOfferFlow = MutableStateFlow<SyncOfferId?>(null)

    private val offerInbox = Channel<Pair<SyncOfferId, SignalingMessage.Offer>>(Channel.CONFLATED)
    private val answerInbox = Channel<Pair<SyncOfferId, SignalingMessage.Answer>>(Channel.UNLIMITED)
    private val iceInbox = Channel<Pair<SyncOfferId, SignalingMessage.IceCandidates>>(Channel.UNLIMITED)
    private val reconnectInbox = Channel<SyncOfferId>(Channel.UNLIMITED)

    init {
        transport.subscribeEnvelopes()
            .onEach(::routeToInbox)
            .launchIn(scope)
    }

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

    override suspend fun awaitIncomingOffer(): EncodedSdp {
        val (offerId, offer) = incomingOffers().first()
        activeOfferFlow.value = offerId
        onOfferIdDetermined(offerId)
        return offer.sdp
    }

    override suspend fun awaitIncomingAnswer(): EncodedSdp {
        val (offerId, answer) = incomingAnswers().first()
        onOfferIdDetermined(offerId)
        return answer.sdp
    }

    override fun subscribeIncomingIceCandidates(): Flow<EncodedIceCandidates> =
        incomingIceCandidates().map { it.candidates }

    fun sendReconnected(offerId: SyncOfferId) {
        sendMessage(offerId, SignalingMessage.Reconnected)
    }

    /** Suspends until the peer asks to drop our current attempt (a Reconnected matching the active offerId). */
    suspend fun awaitReconnectRequest() {
        incomingReconnects().first()
    }

    fun activeOfferId(): SyncOfferId? = activeOfferFlow.value

    private fun incomingOffers(): Flow<Pair<SyncOfferId, SignalingMessage.Offer>> =
        offerInbox.receiveAsFlow().filter { (id, _) -> id != activeOfferFlow.value }

    private fun incomingAnswers(): Flow<Pair<SyncOfferId, SignalingMessage.Answer>> =
        answerInbox.receiveAsFlow().filter { (id, _) -> id == awaitOfferId() }

    private fun incomingIceCandidates(): Flow<SignalingMessage.IceCandidates> =
        iceInbox.receiveAsFlow()
            .filter { (id, _) -> id == awaitOfferId() }
            .map { (_, message) -> message }

    private fun incomingReconnects(): Flow<SyncOfferId> =
        reconnectInbox.receiveAsFlow().filter { it == activeOfferId() }

    private fun sendMessage(offerId: SyncOfferId, message: SignalingMessage) {
        Timber.d("SyncSignaling: sending ${message::class.simpleName} offerId=$offerId")
        transport.send(SyncSignalingEnvelope(offerId, message))
    }

    private fun routeToInbox(envelope: SyncSignalingEnvelope) {
        when (val message = envelope.message) {
            is SignalingMessage.Offer -> offerInbox.trySend(envelope.offerId to message)
            is SignalingMessage.Answer -> answerInbox.trySend(envelope.offerId to message)
            is SignalingMessage.IceCandidates -> iceInbox.trySend(envelope.offerId to message)
            is SignalingMessage.Reconnected -> reconnectInbox.trySend(envelope.offerId)
        }
    }

    private suspend fun awaitOfferId(): SyncOfferId = activeOfferFlow.filterNotNull().first()
}
