package io.paritytech.polkadotapp.feature_products_api.domain

import io.paritytech.polkadotapp.bandersnatch_crypto.ContextualAlias
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId

interface GetContextualAliasUseCase {
    suspend fun getAlias(productAccountId: ProductAccountId): Result<ContextualAlias>
}
