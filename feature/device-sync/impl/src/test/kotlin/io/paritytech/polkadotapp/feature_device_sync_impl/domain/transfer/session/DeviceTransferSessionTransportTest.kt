package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.decodeFromByteArray
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferSessionMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling.SyncSignalingEnvelope
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.CommunicationSessionEvent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.SessionAccount
import io.paritytech.polkadotapp.tools_media_connection_api.domain.signaling.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DeviceTransferSessionTransportTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val communicationSession = FakeCommunicationSession(
        localAccount = SessionAccount.Local(accountId = ByteArray(32) { 0x01 }.toDataByteArray(), pin = null),
        remoteAccount = SessionAccount.Local(accountId = ByteArray(32) { 0x02 }.toDataByteArray(), pin = null),
    )
    private val transport = DeviceTransferSessionTransport(communicationSession, scope)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `signaling envelope goes out wrapped in a session message`() {
        transport.send(envelope(offerId = "o1"))

        val sent = BinaryScale.decodeFromByteArray<DeviceTransferSessionMessageScale>(communicationSession.sent.single())
        assertEquals("o1", (sent as DeviceTransferSessionMessageScale.Signaling).envelope.offerId)
    }

    @Test
    fun `rejection goes out as rejected`() {
        transport.sendRejection()

        assertEquals("00", communicationSession.sent.single().toHexString())
    }

    @Test
    fun `incoming rejection ends the wait for it`() = runBlocking<Unit> {
        receive(requestId = "r1", DeviceTransferSessionMessageScale.Rejected)

        withTimeout(1.seconds) { transport.awaitRejection() }
    }

    @Test
    fun `incoming signaling reaches the envelope subscriber`() = runBlocking<Unit> {
        receive(requestId = "r1", DeviceTransferSessionMessageScale.Signaling(envelope(offerId = "o1")))

        val envelope = withTimeout(1.seconds) { transport.subscribeEnvelopes().first() }

        assertEquals("o1", envelope.offerId)
    }

    @Test
    fun `re-delivered request is routed once`() = runBlocking<Unit> {
        val message = DeviceTransferSessionMessageScale.Signaling(envelope(offerId = "o1"))
        receive(requestId = "r1", message)
        receive(requestId = "r1", message)

        val envelopes = withTimeoutOrNull(200.milliseconds) { transport.subscribeEnvelopes().take(2).toList() }

        assertNull(envelopes)
    }

    private suspend fun receive(requestId: String, message: DeviceTransferSessionMessageScale) {
        val encoded = BinaryScale.encodeToByteArray<DeviceTransferSessionMessageScale>(message)
        communicationSession.events.emit(CommunicationSessionEvent.NewMessagesReceived(requestId, listOf(encoded)))
    }

    private fun envelope(offerId: String) = SyncSignalingEnvelope(offerId, SignalingMessage.Reconnected)
}
