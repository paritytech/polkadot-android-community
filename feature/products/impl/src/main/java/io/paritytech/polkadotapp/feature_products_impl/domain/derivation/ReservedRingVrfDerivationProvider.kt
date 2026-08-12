package io.paritytech.polkadotapp.feature_products_impl.domain.derivation

import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.RingVrfDerivationProvider
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.derivation.ringVrfPath

class ReservedRingVrfDerivationProvider(
    private val productId: ProductId,
    private val index: DerivationIndex32,
) : RingVrfDerivationProvider {
    override fun provideDerivationPath(): String = ringVrfPath(productId, index)
}
