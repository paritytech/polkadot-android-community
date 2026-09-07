package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import java.math.BigInteger
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Every top-up group starts with this, so one query finds them all. */
const val TOP_UP_GROUP_PREFIX = "top-up:"

private const val SEPARATOR = ":"
private const val PART_COUNT = 5

/**
 * The whole top-up, written into the id of the coinage group its transactions are registered under.
 *
 * A group id is caller-chosen and opaque, and a group survives everything a process can do to itself — so
 * putting the operation in the id means the ledger already holds it, and nothing has to keep a parallel copy
 * that could disagree with the transactions it describes.
 *
 * The product's own id is hex-encoded because it is an arbitrary string: the separator would otherwise be
 * ambiguous, and a product could name two top-ups so that one's id read as another's.
 */
@OptIn(ExperimentalTime::class)
fun TopUpOperation.groupId(): CoinageOperationGroupId = CoinageOperationGroupId(
    topUpGroupPrefixOf(productId, id) + "${startedAt.toEpochMilliseconds()}$SEPARATOR${amount.value}"
)

/** Names one top-up whatever it is for and whenever it opened, which is what makes an id unrepeatable. */
fun topUpGroupPrefixOf(productId: ProductId, id: PaymentTopUpId): String =
    "$TOP_UP_GROUP_PREFIX${productId.value}$SEPARATOR${id.value.encodeToByteArray().toHexString()}$SEPARATOR"

/** Null for a group this app did not write, or wrote in a shape it no longer uses. */
@OptIn(ExperimentalTime::class)
fun CoinageOperationGroupId.asTopUpOperation(): TopUpOperation? {
    val parts = value.split(SEPARATOR)
    if (parts.size != PART_COUNT || parts.first() + SEPARATOR != TOP_UP_GROUP_PREFIX) return null

    val (_, productId, encodedId, startedAt, amount) = parts

    return runCatching {
        TopUpOperation(
            id = PaymentTopUpId(encodedId.fromHex().decodeToString()),
            productId = ProductId.fromStoredValue(productId),
            amount = BigInteger(amount).intoBalance(),
            startedAt = Instant.fromEpochMilliseconds(startedAt.toLong()),
        )
    }.getOrNull()
}
