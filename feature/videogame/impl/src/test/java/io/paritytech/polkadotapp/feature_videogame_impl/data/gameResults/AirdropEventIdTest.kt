package io.paritytech.polkadotapp.feature_videogame_impl.data.gameResults

import io.paritytech.polkadotapp.feature_videogame_api.domain.state.model.GameIndex
import junit.framework.TestCase.assertEquals
import org.junit.Test

class AirdropEventIdTest {
    @Test
    fun `event id is the space-padded prefix plus the big-endian game index`() {
        // Mirrors pallets/game airdrop_event_id: 28-byte "pop:game:airdrop:" + game_index.to_be_bytes().
        val expected = "pop:game:airdrop:".toByteArray(Charsets.US_ASCII) +
            ByteArray(11) { ' '.code.toByte() } +
            byteArrayOf(0, 0, 0, 7)

        val actual = AirdropEventId.fromGameIndex(GameIndex(7)).value.value

        assertEquals(32, actual.size)
        assertEquals(expected.toList(), actual.toList())
    }
}
