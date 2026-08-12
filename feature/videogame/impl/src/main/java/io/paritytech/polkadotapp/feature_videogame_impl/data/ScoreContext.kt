package io.paritytech.polkadotapp.feature_videogame_impl.data

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionId
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ProductProofContext
import io.paritytech.polkadotapp.feature_products_api.model.derivation.ReservedProductIds

/**
 * RFC-0024 makes the score context personhood-owned. Only the alias *account* follows that today —
 * the context bytes below stay as they are until the runtime carries the product-scoped constant.
 *
 * TODO RFC-0024: replace [BandersnatchContext.SCORE] with [SCORE_PERSONHOOD_CONTEXT].productContextBytes()
 * once the individuality runtime derives the score context with product_context_bytes.
 */
val BandersnatchContext.Companion.SCORE: BandersnatchContext
    get() = BandersnatchContext.fromString("pop:polkadot.network/score      ")

/**
 * The suffix reuses the padded-string convention the ring collection ids already use, so the
 * constant reads the same on both sides once the chain adopts it.
 */
val SCORE_PERSONHOOD_CONTEXT: ProductProofContext
    get() = ProductProofContext(
        productId = ReservedProductIds.PERSONHOOD,
        suffix = DerivationIndex32.fromBytes(RingCollectionId.paddedString("score").value)
            .getOrElse { error("Padded score suffix is not a valid derivation index: $it") },
    )
