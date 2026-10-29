package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.domain.model.scale.X25519PublicKeyScale
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.RequestContentV1
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.VersionedRequestContent
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatStateScaleConformanceTest {
    private val timestampHex = "0068e5cf8b010000"

    @Test
    fun `blocked chat encodes to the frozen vector`() {
        val entity = SyncEntityScale.ChatsBlocked(
            listOf(ChatBlockStateScale(chatId = ChatIdScale.Contact(ByteArray(32) { 0x01 }), blocked = true))
        )

        assertEquals("04" + "04" + "00" + "01".repeat(32) + "01", encode(entity))
    }

    @Test
    fun `incoming request encodes to the frozen vector`() {
        val request = IncomingChatRequestScale(
            requestId = "r1",
            peer = ByteArray(32) { 0x02 },
            timestamp = 1_700_000_000_000u,
            status = IncomingRequestStatusScale.ACCEPTED,
            peerDevice = LocalDeviceScale(
                statementAccountId = ByteArray(32) { 0x03 },
                encryptionPublicKey = X25519PublicKeyScale(ByteArray(32) { 0x04 }),
                status = DeviceStatusScale.ACTIVE,
                lastUpdate = 5u,
            ),
            pushToken = null,
            welcomeMessage = null,
        )
        val deviceHex = "01" + "03".repeat(32) + "04".repeat(32) + "00" + "0500000000000000"

        assertEquals(
            "05" + "04" + "087231" + "02".repeat(32) + timestampHex + "01" + deviceHex + "00" + "00",
            encode(SyncEntityScale.IncomingRequestsAdded(listOf(request)))
        )
    }

    @Test
    fun `outgoing request encodes to the frozen vector`() {
        val request = OutgoingChatRequestScale(
            requestId = "r2",
            peer = ByteArray(32) { 0x05 },
            timestamp = 1_700_000_000_000u,
            status = OutgoingRequestStatusScale.ACCEPTED,
            content = VersionedRequestContent.V1(RequestContentV1(pushToken = null, welcomeMessage = null)),
        )

        assertEquals(
            "07" + "04" + "087232" + "05".repeat(32) + timestampHex + "01" + "00" + "00" + "00",
            encode(SyncEntityScale.OutgoingRequestsAdded(listOf(request)))
        )
    }

    @Test
    fun `request status updates encode to the frozen vectors`() {
        val incoming = SyncEntityScale.IncomingRequestsStatusUpdated(
            listOf(IncomingRequestStatusUpdateScale(requestId = "r1", status = IncomingRequestStatusScale.DECLINED))
        )
        val outgoing = SyncEntityScale.OutgoingRequestsStatusUpdated(
            listOf(OutgoingRequestStatusUpdateScale(requestId = "r2", status = OutgoingRequestStatusScale.CANCELLED))
        )

        assertEquals("06" + "04" + "087231" + "02", encode(incoming))
        assertEquals("08" + "04" + "087232" + "02", encode(outgoing))
    }

    private fun encode(entity: SyncEntityScale): String {
        return BinaryScale.encodeToByteArray<SyncEntityScale>(entity).toHexString()
    }
}
