package io.paritytech.polkadotapp.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.math.BigInteger

@Entity(
    tableName = "chat_coinage_transfer_detection",
    indices = [
        Index(value = ["messageId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageLocal::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    primaryKeys = ["messageId"]
)
class CoinageTransferDetectionLocal(
    val status: Status,
    val messageId: String,
    val transferredPlanks: BigInteger?
) {
    enum class Status {
        DETECTED, TRANSFERRED, FAILED_DETECTION, FAILED_TRANSFER
    }
}
