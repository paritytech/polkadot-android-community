package io.paritytech.polkadotapp.feature_products_api.model

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld

/**
 * dotNS identities of products the app launches from its own UI, unlike the governance-reserved
 * ones in [io.paritytech.polkadotapp.feature_products_api.model.derivation.ReservedProductIds].
 */
object KnownProductIds {
    const val GET_CASH_LABEL = "getcash"

    fun getCash(tld: DotNsTld): ProductId = ProductId.fromStoredValue(GET_CASH_LABEL + tld.suffix)
}
