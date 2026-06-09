package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi

import io.paritytech.polkadotapp.bandersnatch_crypto.ContextualAlias
import io.paritytech.polkadotapp.feature_products_api.domain.GetContextualAliasUseCase
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_impl.domain.ProductAccountDerivationUseCase
import javax.inject.Inject

class RealGetContextualAliasUseCase @Inject constructor(
    private val productAccountDerivationUseCase: ProductAccountDerivationUseCase,
) : GetContextualAliasUseCase {
    override suspend fun getAlias(productAccountId: ProductAccountId): Result<ContextualAlias> {
        return productAccountDerivationUseCase.deriveContextualAlias(productAccountId)
    }
}
