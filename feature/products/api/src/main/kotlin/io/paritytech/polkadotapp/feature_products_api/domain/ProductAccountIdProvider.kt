package io.paritytech.polkadotapp.feature_products_api.domain

import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId

interface ProductAccountIdProvider {
    suspend fun deriveAccountId(productAccountId: ProductAccountId): Result<EncodedPublicKey>
}
