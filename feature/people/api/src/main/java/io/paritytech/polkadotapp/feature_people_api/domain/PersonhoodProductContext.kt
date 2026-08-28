package io.paritytech.polkadotapp.feature_people_api.domain

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.toLittleEndianBytes
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.productContext

private const val PERSONHOOD_PRODUCT_NAME = "peopl"
private val SYSTEM_PREFIX = "sys/".encodeToByteArray()

/**
 * Context of an account owned by the personhood product. [networkSuffix] is the network's own dotNS
 * suffix as the runtime reports it, so the product name matches the reserved identity on chain.
 */
fun personhoodProductContext(networkSuffix: ByteArray, suffix: DerivationIndex32): BandersnatchContext {
    return productContext("$PERSONHOOD_PRODUCT_NAME.${networkSuffix.decodeToString()}", suffix)
}

/**
 * Selector of a personhood account that a pallet derives on its own instead of enumerating by index.
 * [family] names the deriving pallet, [first] and [tail] carry that family's own key.
 */
fun personhoodSystemSuffix(family: UInt, first: UInt, tail: ByteArray): DerivationIndex32 {
    val raw = ByteArray(DerivationIndex32.SIZE_BYTES)
    SYSTEM_PREFIX.copyInto(raw, destinationOffset = 0)
    family.toLittleEndianBytes().copyInto(raw, destinationOffset = 4)
    first.toLittleEndianBytes().copyInto(raw, destinationOffset = 8)
    tail.copyInto(raw, destinationOffset = 12)

    return DerivationIndex32.fromBytes(raw.toDataByteArray())
        .getOrElse { error("Personhood system suffix is not a valid derivation index: $it") }
}
