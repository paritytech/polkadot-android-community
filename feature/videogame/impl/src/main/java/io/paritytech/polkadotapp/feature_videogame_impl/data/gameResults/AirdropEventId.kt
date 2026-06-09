package io.paritytech.polkadotapp.feature_videogame_impl.data.gameResults

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.toBigEndianByteArray
import io.paritytech.polkadotapp.feature_videogame_api.domain.state.model.GameIndex

/**
 * Storage key of a pallet-airdrop event: `"pop:game:airdrop:"` space-padded to 28 bytes,
 * followed by the game index as a big-endian u32 — mirrors `Pallet::airdrop_event_id`
 * (`game_index.to_be_bytes()`).
 */
@JvmInline
value class AirdropEventId(val value: DataByteArray) {
    companion object {
        private const val PREFIX = "pop:game:airdrop:"
        private const val BASE_LENGTH = 28
        private const val SPACE = ' '.code.toByte()

        fun fromGameIndex(gameIndex: GameIndex): AirdropEventId {
            val prefix = PREFIX.toByteArray(Charsets.US_ASCII)
            val padded = prefix + ByteArray(BASE_LENGTH - prefix.size) { SPACE }
            return AirdropEventId((padded + gameIndex.value.toBigEndianByteArray()).toDataByteArray())
        }
    }
}
