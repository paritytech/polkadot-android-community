package io.paritytech.polkadotapp.feature_videogame_impl.data

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionId
import io.paritytech.polkadotapp.feature_people_api.domain.personhoodProductContext
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ProductProofContext
import io.paritytech.polkadotapp.feature_products_api.model.derivation.ReservedProductIds

private const val SCORE_INDEX = 0u

internal fun BandersnatchContext.Companion.score(networkSuffix: ByteArray): BandersnatchContext {
    return personhoodProductContext(networkSuffix, DerivationIndex32.fromUInt(SCORE_INDEX))
}

/**
 * The suffix reuses the padded-string convention the ring collection ids already use, so the
 * constant reads the same on both sides once the chain adopts it.
 */
fun scorePersonhoodContext(tld: DotNsTld): ProductProofContext {
    return ProductProofContext(
        productId = ReservedProductIds.personhood(tld),
        suffix = DerivationIndex32.fromBytes(RingCollectionId.paddedString("score").value)
            .getOrElse { error("Padded score suffix is not a valid derivation index: $it") },
    )
}
