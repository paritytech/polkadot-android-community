package io.paritytech.polkadotapp.airdrop_vrf

import android.util.Log
import io.novasama.substrate_sdk_android.encrypt.Sr25519
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirdropVrfCryptoTest {

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun signsAndReturnsStableNinetySixByteOutput() {
        val keypair = Sr25519.keypairFromSeed(ByteArray(32) { 1 })
        val eventId = eventId(gameIndex = 7)

        val first = AirdropVrfSigner.sign(keypair, eventId).getOrThrow()
        val second = AirdropVrfSigner.sign(keypair, eventId).getOrThrow()

        assertEquals(32, first.preOutput.size)
        assertEquals(64, first.proof.size)
        // The VRF pre-output (the lottery seed) is deterministic for a given key + event id; the
        // proof carries fresh witness randomness and may differ between calls.
        assertArrayEquals(first.preOutput, second.preOutput)

        Log.d("AirdropVrfCryptoTest", "preOutput=${first.preOutput.toHexString()}")
    }

    @Test
    fun rejectsBadLengths() {
        val keypair = Sr25519.keypairFromSeed(ByteArray(32) { 1 })

        assertTrue(AirdropVrfSigner.sign(ByteArray(10), eventId(1)).isFailure)
        assertTrue(AirdropVrfSigner.sign(keypair, ByteArray(10)).isFailure)
    }

    // "pop:game:airdrop:" + 11 spaces + game_index.to_be_bytes() = 32 bytes.
    private fun eventId(gameIndex: Int): ByteArray =
        "pop:game:airdrop:           ".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(
                (gameIndex ushr 24).toByte(),
                (gameIndex ushr 16).toByte(),
                (gameIndex ushr 8).toByte(),
                gameIndex.toByte(),
            )
}
