package io.paritytech.polkadotapp.feature_products_impl.domain.derivation

import io.paritytech.polkadotapp.feature_account_api.domain.derivation.AccountDerivationProvider
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.derivation.productAccountPath

/**
 * RFC-0022: a built-in account is the first account of the product that owns the feature.
 */
class ReservedProductAccountDerivationProvider(
    private val productId: ProductId,
) : AccountDerivationProvider {
    override fun provideDerivationPath(): String = productAccountPath(productId, DerivationIndex32.default())
}
