package io.paritytech.polkadotapp.feature_products_api.model.derivation

import io.paritytech.polkadotapp.feature_products_api.model.ProductId

/**
 * RFC-0022 governance-reserved dotNS identities for built-in app features.
 */
object ReservedProductIds {
    val GAME = ProductId.fromStoredValue("dim2.dot")

    val PROOF_OF_INK = ProductId.fromStoredValue("poi.dot")

    val FUNDING = ProductId.fromStoredValue("fund.dot")

    val LIGHT_PERSON_IDENTITY = ProductId.fromStoredValue("uid.dot")

    val PERSONHOOD = ProductId.fromStoredValue("peopl.dot")
}
