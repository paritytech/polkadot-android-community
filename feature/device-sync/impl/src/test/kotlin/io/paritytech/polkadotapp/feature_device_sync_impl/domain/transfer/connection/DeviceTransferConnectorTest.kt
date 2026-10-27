package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.connection

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.DeviceTransferSessionMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling.SyncPeerChannelSignaling
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session.DeviceTransferRole
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session.DeviceTransferSession
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session.DeviceTransferSessionTransport
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session.FakeCommunicationSession
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.CommunicationSessionEvent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.SessionAccount
import io.paritytech.polkadotapp.test_shared.FakeTimeProvider
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransportState
import io.paritytech.polkadotapp.tools_media_connection_api.domain.GroupPeerConnection
import io.paritytech.polkadotapp.tools_media_connection_api.domain.PeerChannel
import io.paritytech.polkadotapp.tools_media_connection_api.domain.PeerChannelFactory
import io.paritytech.polkadotapp.tools_media_connection_api.domain.PeerChannelSignaling
import io.paritytech.polkadotapp.tools_media_connection_api.domain.PeerConnectionLogger
import io.paritytech.polkadotapp.tools_media_connection_api.domain.UseCaseData
import io.paritytech.polkadotapp.tools_media_connection_api.domain.UseCaseId
import io.paritytech.polkadotapp.tools_media_connection_api.domain.models.MediaConfiguration
import io.paritytech.polkadotapp.tools_media_connection_api.domain.models.MediaState
import io.paritytech.polkadotapp.tools_media_connection_api.domain.models.MediaTracks
import io.paritytech.polkadotapp.tools_media_connection_api.domain.models.PeerChannelConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DeviceTransferConnectorTest {
    private val channelFactory = FakePeerChannelFactory()

    @Test
    fun `sender connects as the initiator and finishes when the channel work is done`() = runTest {
        channelFactory.channels += FakePeerChannel(opens = true)

        val result = connector().connect(session(DeviceTransferRole.SENDER)) { }

        assertEquals(DeviceTransferConnectionResult.FINISHED, result)
        assertEquals(listOf(true), channelFactory.initiatorFlags)
        assertTrue(channelFactory.created.single().disposed)
    }

    @Test
    fun `receiver connects as the acceptor`() = runTest {
        channelFactory.channels += FakePeerChannel(opens = true)

        val result = connector().connect(session(DeviceTransferRole.RECEIVER)) { }

        assertEquals(DeviceTransferConnectionResult.FINISHED, result)
        assertEquals(listOf(false), channelFactory.initiatorFlags)
    }

    @Test
    fun `dropped channel is replaced and the work continues on the new one`() = runTest {
        val first = FakePeerChannel(opens = true)
        channelFactory.channels += first
        channelFactory.channels += FakePeerChannel(opens = true)
        var uses = 0

        val result = connector().connect(session(DeviceTransferRole.SENDER)) {
            uses++
            if (uses == 1) {
                first.connectionState.value = PeerChannelConnectionState.Failed
                awaitCancellation()
            }
        }

        assertEquals(DeviceTransferConnectionResult.FINISHED, result)
        assertEquals(2, uses)
        assertEquals(2, channelFactory.created.size)
    }

    @Test
    fun `receiver stops when the sender rejects`() = runTest {
        val communicationSession = communicationSession()
        val rejected = BinaryScale.encodeToByteArray<DeviceTransferSessionMessageScale>(
            DeviceTransferSessionMessageScale.Rejected
        )
        communicationSession.events.emit(CommunicationSessionEvent.NewMessagesReceived("r1", listOf(rejected)))

        val result = connector().connect(session(DeviceTransferRole.RECEIVER, communicationSession)) { }

        assertEquals(DeviceTransferConnectionResult.REJECTED, result)
    }

    @Test
    fun `gives up after fifteen minutes without a connection`() = runTest {
        val result = connector().connect(session(DeviceTransferRole.SENDER)) { }

        assertEquals(DeviceTransferConnectionResult.TIMED_OUT, result)
        assertTrue(channelFactory.created.isNotEmpty())
    }

    private fun TestScope.connector() = DeviceTransferConnector(
        peerChannelFactory = channelFactory,
        timeProvider = FakeTimeProvider { testScheduler.currentTime },
    )

    private fun TestScope.session(
        role: DeviceTransferRole,
        communicationSession: FakeCommunicationSession = communicationSession(),
    ): DeviceTransferSession {
        val transport = DeviceTransferSessionTransport(communicationSession, backgroundScope)
        val signaling = SyncPeerChannelSignaling(transport = transport, scope = backgroundScope, onOfferIdDetermined = {})
        return DeviceTransferSession(UUID.randomUUID(), role, signaling, transport, backgroundScope)
    }

    private fun communicationSession() = FakeCommunicationSession(
        localAccount = SessionAccount.Local(accountId = ByteArray(32) { 0x01 }.toDataByteArray(), pin = null),
        remoteAccount = SessionAccount.Local(accountId = ByteArray(32) { 0x02 }.toDataByteArray(), pin = null),
    )

    private class FakePeerChannelFactory : PeerChannelFactory {
        val channels = ArrayDeque<FakePeerChannel>()
        val created = mutableListOf<FakePeerChannel>()
        val initiatorFlags = mutableListOf<Boolean>()

        override suspend fun createSingleConnection(
            signaling: PeerChannelSignaling,
            mediaConfiguration: MediaConfiguration,
            scope: CoroutineScope,
            isInitiator: Boolean,
            logger: PeerConnectionLogger,
        ): PeerChannel {
            initiatorFlags += isInitiator
            val channel = channels.removeFirstOrNull() ?: FakePeerChannel(opens = false)
            created += channel
            return channel
        }

        override suspend fun createGroupConnection(
            mediaConfiguration: MediaConfiguration,
            scope: CoroutineScope,
        ): GroupPeerConnection = error("Not used by device transfer")
    }

    private class FakePeerChannel(opens: Boolean) : PeerChannel {
        var disposed = false

        override val connectionState = MutableStateFlow(PeerChannelConnectionState.Connecting)
        override val dataTransport: DataTransport = FakeDataTransport(opens)
        override val mediaTracks: StateFlow<MediaTracks> = MutableStateFlow(MediaTracks())
        override val mediaState: StateFlow<MediaState> = MutableStateFlow(MediaState())

        override fun startConnection() = Unit

        override fun dispose() {
            disposed = true
        }

        override suspend fun setLocalCameraEnabled(enabled: Boolean) = Unit

        override suspend fun setLocalMicrophoneEnabled(enabled: Boolean) = Unit
    }

    private class FakeDataTransport(opens: Boolean) : DataTransport {
        private val opened = CompletableDeferred<Unit>().apply { if (opens) complete(Unit) }

        override val state: StateFlow<DataTransportState> = MutableStateFlow(DataTransportState.Connecting)

        override fun subscribeMessages(id: UseCaseId): Flow<UseCaseData> = emptyFlow()

        override suspend fun send(id: UseCaseId, data: UseCaseData) = Unit

        override suspend fun awaitOpen() {
            opened.await()
        }

        override fun isOpen(): Boolean = opened.isCompleted
    }
}
