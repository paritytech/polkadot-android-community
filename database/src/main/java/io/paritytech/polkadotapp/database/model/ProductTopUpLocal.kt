package io.paritytech.polkadotapp.database.model

import androidx.room.Entity
import java.math.BigInteger

/**
 * An RFC-0006 top-up a product asked for, and everything about it the product may be told.
 *
 * Kept forever, and keyed by the product's own idempotency key, because that is what a top-up's contract
 * rests on: an id names one operation for good, and a product that asks after one — however long afterwards,
 * however many restarts later — gets the same answer.
 *
 * The source the funds are drawn from is deliberately absent: it is secret, so it lives encrypted elsewhere
 * and only for as long as another attempt could still need it. So is the coinage group its transactions are
 * registered under, which [topUpId] determines.
 */
@Entity(tableName = "product_top_ups", primaryKeys = ["productId", "topUpId"])
class ProductTopUpLocal(
    val productId: String,
    /** The product's 32-byte idempotency key, as hex. */
    val topUpId: String,
    val amountPlanks: BigInteger,
    /** When the operation opened, which is what its retry window runs from — never when it was resumed. */
    val startedAtMillis: Long,
    /** Null while the top-up is still running. Once set, it is the last word and never changes. */
    val outcome: Outcome?,
    /** Only ever set alongside [Outcome.CLAIMED_PARTIALLY]. */
    val actualClaimedPlanks: BigInteger?,
) {
    enum class Outcome {
        CLAIMED,
        CLAIMED_PARTIALLY,
        NOT_CLAIMED,
    }
}
