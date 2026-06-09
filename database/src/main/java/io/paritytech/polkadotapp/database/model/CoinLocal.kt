package io.paritytech.polkadotapp.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coins")
class CoinLocal(
    @PrimaryKey val derivationIndex: Int,
    val accountId: ByteArray,
    val valueExponent: Int,
    val ageValue: Int?,
    val spentState: SpentState
) {
    enum class SpentState {
        SPENT_LOCALLY,
        SPENT_ON_CHAIN,
        NOT_SPENT
    }
}
