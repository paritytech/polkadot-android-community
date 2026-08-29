package io.paritytech.polkadotapp.tools_integrity_impl.data.claim

import io.novasama.substrate_sdk_android.extensions.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceEnvelopeEncoderTest {
    private val challenge = ByteArray(32) { 0x11 }
    private val candidate = ByteArray(32) { 0x22 }
    private val widevineId = ByteArray(16) { 0x33 }

    // Cross-repo golden vector: moving these bytes is a wire break, not a refactor.
    @Test
    fun `envelope matches the golden bytes`() {
        val encoded = DeviceEnvelopeEncoder.encode(
            challenge = challenge,
            candidate = candidate,
            widevineId = widevineId,
            level = WIDEVINE_LEVEL_L1
        )

        val expected = listOf(
            "a6", // map(6)
            "00", // key 0
            "73", // text(19)
            "6475622f706f75642f616e64726f69642f7631", // "dub/poud/android/v1"
            "01", // key 1
            "01", // version 1
            "02", // key 2
            "5820", // bytes(32)
            "11".repeat(32),
            "03", // key 3
            "5820", // bytes(32)
            "22".repeat(32),
            "04", // key 4
            "50", // bytes(16)
            "33".repeat(16),
            "05", // key 5
            "01" // level 1
        ).joinToString("")

        assertEquals(expected, encoded.toHexString())
    }

    @Test
    fun `out of spec inputs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceEnvelopeEncoder.encode(ByteArray(31), candidate, widevineId, WIDEVINE_LEVEL_L1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceEnvelopeEncoder.encode(challenge, ByteArray(33), widevineId, WIDEVINE_LEVEL_L1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceEnvelopeEncoder.encode(challenge, candidate, ByteArray(0), WIDEVINE_LEVEL_L1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceEnvelopeEncoder.encode(challenge, candidate, ByteArray(65), WIDEVINE_LEVEL_L1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceEnvelopeEncoder.encode(challenge, candidate, widevineId, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceEnvelopeEncoder.encode(challenge, candidate, widevineId, 2)
        }
    }

    @Test
    fun `widevine id bounds are inclusive`() {
        val minId = DeviceEnvelopeEncoder.encode(challenge, candidate, byteArrayOf(0x7F), WIDEVINE_LEVEL_L3)
        // Tail: key 4, bytes(1) 0x7f, key 5, level 3.
        assertEquals("04417f0503", minId.copyOfRange(minId.size - 5, minId.size).toHexString())

        val maxId = DeviceEnvelopeEncoder.encode(challenge, candidate, ByteArray(64) { 0x44 }, WIDEVINE_LEVEL_L1)
        // Tail: key 4, bytes(64) in shortest form, the id, key 5, level 1.
        assertEquals("045840" + "44".repeat(64) + "0501", maxId.copyOfRange(maxId.size - 69, maxId.size).toHexString())
    }
}
