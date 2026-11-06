package io.paritytech.polkadotapp.feature_products_api.model.derivation

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_products_api.model.ProductId

/**
 * RFC-0022 governance-reserved dotNS identities for built-in app features.
 *
 * Reserved names live in each network's own namespace (`dim2.dot` on Polkadot, `dim2.paseo`
 * on Paseo), so every id is a function of the network TLD — matching the runtime's
 * network-specific product providers (individuality#1300).
 *
 * [personhood] is both the ring-VRF key domain and the owner of the wallet's main account:
 * `//product//peopl.<tld>/index_bytes(0)` owns the person's dotNS usernames
 * (individuality-apps#1639). There is no separate `uid` identity product.
 */
object ReservedProductIds {
    fun game(tld: DotNsTld): ProductId = reserved("dim2", tld)

    fun funding(tld: DotNsTld): ProductId = reserved("fund", tld)

    fun personhood(tld: DotNsTld): ProductId = reserved("peopl", tld)

    fun chat(tld: DotNsTld): ProductId = reserved("chat", tld)

    private fun reserved(label: String, tld: DotNsTld): ProductId {
        return ProductId.fromStoredValue(label + tld.suffix)
    }
}
