package io.paritytech.polkadotapp.feature_products_impl.domain.productSettings

import io.paritytech.polkadotapp.feature_products_api.model.Product
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProductSettingsInteractor @Inject constructor(
    private val productRepository: ProductRepository
) {
    fun observeProduct(productId: ProductId): Flow<Product?> {
        return productRepository.observeProducts()
            .map { products -> products.find { it.id == productId } }
    }
}
