package io.paritytech.polkadotapp.database.model

import androidx.room.Entity

@Entity(tableName = "recycler_vouchers", primaryKeys = ["installationId", "ringVrfKeyIndex"])
class RecyclerVoucherLocal(
    val installationId: ByteArray,
    val ringVrfKeyIndex: Int,
    val ringVrfPublicKey: ByteArray,
    val recyclerValue: Int,
    val locationRecyclerIndex: Int?,
    val recyclerMembers: Int?,
    val enteredAt: Long?,
)
