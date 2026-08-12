package io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.intoBandersnatchContext
import io.paritytech.polkadotapp.common.utils.blake2b256
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_products_api.model.ProductId

data class ProductProofContext(
    val productId: ProductId,
    val suffix: DerivationIndex32,
) {
    // RFC-0004: blake2b256(utf8("product/") ++ utf8(productId) ++ utf8("/") ++ suffix).
    fun productContextBytes(): BandersnatchContext {
        val prefix = "product/${productId.value}/".encodeToByteArray()
        return (prefix + suffix.bytes.value).blake2b256().intoBandersnatchContext()
    }
}
