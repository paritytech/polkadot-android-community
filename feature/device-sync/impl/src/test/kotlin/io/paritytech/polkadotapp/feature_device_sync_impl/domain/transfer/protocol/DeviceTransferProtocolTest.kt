package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.decodeFromByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.ChatIdScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncMessageScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncUpdateScale
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransportState
import io.paritytech.polkadotapp.tools_media_connection_api.domain.UseCaseData
import io.paritytech.polkadotapp.tools_media_connection_api.domain.UseCaseId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTransferProtocolTest {
    private val deviceKey = ByteArray(32) { 0x07 }.toDataByteArray()
    private val updates = listOf(update(id = 1u, contacts = 1), update(id = 2u, contacts = 2), update(id = 3u, contacts = 1))

    @Test
    fun `snapshot reaches the receiver and both sides complete`() = runTest {
        val applied = mutableListOf<UInt>()
        val sender = DeviceTransferSender(updates, deviceKey)
        val receiver = DeviceTransferReceiver { applied += it.id }
        val (senderTransport, receiverTransport) = LinkedDataTransport.pair()

        val receiverOutcome = async(start = CoroutineStart.UNDISPATCHED) { receiver.run(receiverTransport) }
        val senderOutcome = sender.run(senderTransport)

        assertEquals(DeviceTransferSenderOutcome.Completed, senderOutcome)
        assertEquals(DeviceTransferReceiverOutcome.Completed(deviceKey), receiverOutcome.await())
        assertEquals(listOf(1u, 2u, 3u), applied)
        assertEquals(DeviceTransferProgress(appliedChanges = 4, totalChanges = 4), receiver.progress.value)
    }

    @Test
    fun `after a reconnect the sender resumes from the first unacknowledged update`() = runTest {
        val applied = mutableListOf<UInt>()
        var holdSecondUpdate = true
        val sender = DeviceTransferSender(updates, deviceKey)
        val receiver = DeviceTransferReceiver { update ->
            applied += update.id
            if (update.id == 2u && holdSecondUpdate) awaitCancellation()
        }

        val (firstSenderTransport, firstReceiverTransport) = LinkedDataTransport.pair()
        val firstReceiverRun = async(start = CoroutineStart.UNDISPATCHED) { receiver.run(firstReceiverTransport) }
        val firstSenderRun = async(start = CoroutineStart.UNDISPATCHED) { sender.run(firstSenderTransport) }
        advanceUntilIdle()
        firstSenderRun.cancel()
        firstReceiverRun.cancel()

        holdSecondUpdate = false
        val (secondSenderTransport, secondReceiverTransport) = LinkedDataTransport.pair()
        val secondReceiverRun = async(start = CoroutineStart.UNDISPATCHED) { receiver.run(secondReceiverTransport) }
        val senderOutcome = sender.run(secondSenderTransport)

        assertEquals(DeviceTransferSenderOutcome.Completed, senderOutcome)
        assertEquals(DeviceTransferReceiverOutcome.Completed(deviceKey), secondReceiverRun.await())
        assertEquals(listOf(1u, 2u), firstSenderTransport.sentUpdateIds())
        assertEquals(listOf(2u, 3u), secondSenderTransport.sentUpdateIds())
        assertEquals(listOf(1u, 2u, 2u, 3u), applied)
        assertEquals(4, receiver.progress.value.appliedChanges)
    }

    @Test
    fun `repeated update is acknowledged again but applied once`() = runTest {
        val applied = mutableListOf<UInt>()
        val receiver = DeviceTransferReceiver { applied += it.id }
        val (peerTransport, receiverTransport) = LinkedDataTransport.pair()
        val receiverRun = async(start = CoroutineStart.UNDISPATCHED) { receiver.run(receiverTransport) }

        peerTransport.sendSyncMessage(SyncMessageScale.Update(updates.first()))
        peerTransport.sendSyncMessage(SyncMessageScale.Update(updates.first()))
        advanceUntilIdle()
        receiverRun.cancel()

        assertEquals(listOf(1u), applied)
        assertEquals(listOf(1u, 1u), receiverTransport.sentAckIds())
    }

    @Test
    fun `cancelling the sender aborts the transfer on the receiver`() = runTest {
        val sender = DeviceTransferSender(updates, deviceKey)
        val receiver = DeviceTransferReceiver { awaitCancellation() }
        val (senderTransport, receiverTransport) = LinkedDataTransport.pair()

        val receiverOutcome = async(start = CoroutineStart.UNDISPATCHED) { receiver.run(receiverTransport) }
        sender.cancel()
        val senderOutcome = sender.run(senderTransport)

        assertEquals(DeviceTransferSenderOutcome.Cancelled, senderOutcome)
        assertEquals(
            DeviceTransferReceiverOutcome.AbortedBySender(DeviceTransferAbortReason.USER_CANCELLED),
            receiverOutcome.await()
        )
    }

    @Test
    fun `cancelling the receiver aborts the transfer on the sender`() = runTest {
        val sender = DeviceTransferSender(updates, deviceKey)
        val receiver = DeviceTransferReceiver { awaitCancellation() }
        val (senderTransport, receiverTransport) = LinkedDataTransport.pair()

        val senderOutcome = async(start = CoroutineStart.UNDISPATCHED) { sender.run(senderTransport) }
        receiver.cancel()
        val receiverOutcome = receiver.run(receiverTransport)

        assertEquals(DeviceTransferReceiverOutcome.Cancelled, receiverOutcome)
        assertEquals(
            DeviceTransferSenderOutcome.AbortedByReceiver(DeviceTransferAbortReason.USER_CANCELLED),
            senderOutcome.await()
        )
    }

    private fun update(id: UInt, contacts: Int) = SyncUpdateScale(
        id = id,
        entities = listOf(SyncEntityScale.ChatsAdded(List(contacts) { ChatIdScale.Contact(ByteArray(32) { it.toByte() }) })),
        timePoint = 1_000uL,
    )

    private class LinkedDataTransport : DataTransport {
        private lateinit var peer: LinkedDataTransport
        private val incoming = MutableSharedFlow<Pair<UseCaseId, UseCaseData>>(replay = 64)
        private val sent = mutableListOf<Pair<UseCaseId, UseCaseData>>()

        override val state: StateFlow<DataTransportState> = MutableStateFlow(DataTransportState.Open)

        override fun subscribeMessages(id: UseCaseId): Flow<UseCaseData> {
            return incoming.filter { (useCase, _) -> useCase == id }.map { (_, data) -> data }
        }

        override suspend fun send(id: UseCaseId, data: UseCaseData) {
            sent += id to data
            peer.incoming.emit(id to data)
        }

        override suspend fun awaitOpen() = Unit

        override fun isOpen(): Boolean = true

        fun sentUpdateIds(): List<UInt> = sentSyncMessages().filterIsInstance<SyncMessageScale.Update>().map { it.update.id }

        fun sentAckIds(): List<UInt> = sentSyncMessages().filterIsInstance<SyncMessageScale.Ack>().map { it.ack.id }

        private fun sentSyncMessages(): List<SyncMessageScale> {
            return sent.filter { (useCase, _) -> useCase == "device-sync" }
                .map { (_, data) -> BinaryScale.decodeFromByteArray<SyncMessageScale>(data) }
        }

        companion object {
            fun pair(): Pair<LinkedDataTransport, LinkedDataTransport> {
                val first = LinkedDataTransport()
                val second = LinkedDataTransport()
                first.peer = second
                second.peer = first
                return first to second
            }
        }
    }
}
