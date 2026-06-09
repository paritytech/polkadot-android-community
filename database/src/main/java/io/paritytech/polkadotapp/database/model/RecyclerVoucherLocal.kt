package io.paritytech.polkadotapp.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recycler_vouchers")
class RecyclerVoucherLocal(
    @PrimaryKey val ringVrfKeyIndex: Int,
    val ringVrfPublicKey: ByteArray,
    val recyclerValue: Int,
    val locationRecyclerIndex: Int?,
    val allocatedAt: Long,
    val delayUnloadUntil: Long,
    val ringHasEnoughRingMembersToWithdraw: Boolean,
    val usageState: UsageState
) {
    enum class UsageState {
        USED_LOCALLY,
        USED_ON_CHAIN,
        NOT_USED
    }
}
