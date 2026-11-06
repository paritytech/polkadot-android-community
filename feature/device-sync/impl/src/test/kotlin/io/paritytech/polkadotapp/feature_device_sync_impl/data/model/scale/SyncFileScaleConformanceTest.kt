package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.decodeFromByteArray
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.utils.decodeFromByteArrayCatching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Byte-level pins for the `device-sync-file` wire format (File Transfer RFC §2). The vectors are the ones the
 * iOS client pins, so codec drift on either side fails here instead of as an undecodable message on the peer.
 */
class SyncFileScaleConformanceTest {
    // u8 tag 0 · String "m1" · ByteArray [aa bb]
    private val attachmentHex = "00" + "086d31" + "08aabb"

    @Test
    fun `filesRequest encodes to the frozen vector`() {
        val message = SyncFileMessageScale.FilesRequest(
            FilesRequestScale(requestId = 7u, files = listOf(FileRequestItemScale(attachment(), offset = 4_096u)))
        )

        assertRoundTrip("00" + "07000000" + "04" + attachmentHex + "0010000000000000", message)
    }

    @Test
    fun `header encodes to the frozen vector`() {
        val message = SyncFileMessageScale.Header(FileHeaderScale(requestId = 7u, fileIndex = 2u, totalSize = 300u))

        assertRoundTrip("01" + "07000000" + "02000000" + "2c01000000000000", message)
    }

    @Test
    fun `chunk encodes to the frozen vector`() {
        val message = SyncFileMessageScale.Chunk(
            FileChunkScale(requestId = 7u, fileIndex = 0u, offset = 4_096u, data = byteArrayOf(1, 2, 3))
        )

        assertRoundTrip("02" + "07000000" + "00000000" + "0010000000000000" + "0c010203", message)
    }

    @Test
    fun `unavailable encodes to the frozen vector`() {
        val message = SyncFileMessageScale.Unavailable(
            FileUnavailableScale(requestId = 7u, fileIndex = 1u, reason = FileUnavailableReasonScale.DOWNLOADING)
        )

        assertRoundTrip("03" + "07000000" + "01000000" + "01", message)
    }

    @Test
    fun `cancelFilesRequest and completed encode to the frozen vectors`() {
        assertRoundTrip("04" + "07000000", SyncFileMessageScale.CancelFilesRequest(requestId = 7u))
        assertRoundTrip("05" + "07000000", SyncFileMessageScale.Completed(requestId = 7u))
    }

    @Test
    fun `availabilityRequest encodes to the frozen vector`() {
        val message = SyncFileMessageScale.AvailabilityRequest(
            FilesAvailabilityRequestScale(requestId = 9u, files = listOf(attachment()))
        )

        assertRoundTrip("06" + "09000000" + "04" + attachmentHex, message)
    }

    @Test
    fun `availabilityResponse encodes to the frozen vector`() {
        val message = SyncFileMessageScale.AvailabilityResponse(
            FilesAvailabilityResponseScale(
                requestId = 9u,
                availability = listOf(
                    FileAvailabilityScale.Available(totalSize = 300u),
                    FileAvailabilityScale.Unavailable(FileUnavailableReasonScale.NOT_STORED),
                )
            )
        )

        assertRoundTrip("07" + "09000000" + "08" + "002c01000000000000" + "0100", message)
    }

    @Test
    fun `header decodes from the frozen vector`() {
        val decoded = BinaryScale.decodeFromByteArray<SyncFileMessageScale>(("01" + "07000000" + "02000000" + "2c01000000000000").fromHex())

        val header = (decoded as SyncFileMessageScale.Header).header
        assertEquals(7u, header.requestId)
        assertEquals(2u, header.fileIndex)
        assertEquals(300uL, header.totalSize)
    }

    @Test
    fun `unknown enum indices fail to decode`() {
        val vectors = listOf(
            "08" + "07000000",
            "03" + "07000000" + "01000000" + "02",
            "00" + "07000000" + "04" + "01086d3108aabb" + "0000000000000000",
            "07" + "09000000" + "04" + "02",
        )

        vectors.forEach { hex ->
            val decoded = BinaryScale.decodeFromByteArrayCatching<SyncFileMessageScale>(hex.fromHex())
            assertTrue("$hex must not decode", decoded.isFailure)
        }
    }

    @Test
    fun `a full chunk with its data channel framing stays below the 64 KB SCTP limit`() {
        val chunk = SyncFileMessageScale.Chunk(
            FileChunkScale(requestId = UInt.MAX_VALUE, fileIndex = UInt.MAX_VALUE, offset = ULong.MAX_VALUE, data = ByteArray(60 * 1024))
        )
        val payloadSize = BinaryScale.encodeToByteArray<SyncFileMessageScale>(chunk).size
        // DataChannelMessage: compact-prefixed "device-sync-file" id + compact-prefixed payload.
        val envelopeSize = 1 + "device-sync-file".length + 4 + payloadSize

        assertTrue(envelopeSize < 64 * 1024)
    }

    private fun attachment(): FileReferenceScale = FileReferenceScale.MessageAttachment(
        MessageAttachmentIdScale(messageId = "m1", identifier = byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
    )

    private fun assertRoundTrip(hex: String, message: SyncFileMessageScale) {
        assertEquals(hex, BinaryScale.encodeToByteArray(message).toHexString())

        val decoded = BinaryScale.decodeFromByteArray<SyncFileMessageScale>(hex.fromHex())
        assertEquals(hex, BinaryScale.encodeToByteArray(decoded).toHexString())
    }
}
