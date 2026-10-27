package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.decodeFromByteArray
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.signaling.SyncSignalingEnvelope
import io.paritytech.polkadotapp.tools_media_connection_api.domain.signaling.SignalingMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTransferSessionMessageScaleConformanceTest {
    private val rejectedHex = "00"
    private val signalingHex = "01" + "086f31" + "01" + "080102"

    @Test
    fun `rejected encodes to the frozen vector`() {
        val encoded = BinaryScale.encodeToByteArray<DeviceTransferSessionMessageScale>(
            DeviceTransferSessionMessageScale.Rejected
        )

        assertEquals(rejectedHex, encoded.toHexString())
    }

    @Test
    fun `signaling encodes to the frozen vector`() {
        val message = DeviceTransferSessionMessageScale.Signaling(
            SyncSignalingEnvelope(offerId = "o1", message = SignalingMessage.Offer(sdp = byteArrayOf(1, 2)))
        )

        assertEquals(signalingHex, BinaryScale.encodeToByteArray<DeviceTransferSessionMessageScale>(message).toHexString())
    }

    @Test
    fun `frozen vectors decode to rejected and signaling`() {
        val rejected = BinaryScale.decodeFromByteArray<DeviceTransferSessionMessageScale>(rejectedHex.fromHex())
        val signaling = BinaryScale.decodeFromByteArray<DeviceTransferSessionMessageScale>(signalingHex.fromHex())

        assertEquals(DeviceTransferSessionMessageScale.Rejected, rejected)
        val envelope = (signaling as DeviceTransferSessionMessageScale.Signaling).envelope
        assertEquals("o1", envelope.offerId)
        assertArrayEquals(byteArrayOf(1, 2), (envelope.message as SignalingMessage.Offer).sdp)
    }
}
