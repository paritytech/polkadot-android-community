package io.paritytech.polkadotapp.database.model

import androidx.room.Entity

@Entity(tableName = "coins", primaryKeys = ["installationId", "derivationIndex"])
class CoinLocal(
    val installationId: ByteArray,
    val derivationIndex: Int,
    val accountId: ByteArray,
    val valueExponent: Int,
    /** The last age the chain was seen to hold, never cleared once known — see Coin.Age. */
    val ageValue: Int?,
    /** Whether the chain holds the coin right now. Kept apart from [ageValue] on purpose. */
    val onChain: Boolean,
)
