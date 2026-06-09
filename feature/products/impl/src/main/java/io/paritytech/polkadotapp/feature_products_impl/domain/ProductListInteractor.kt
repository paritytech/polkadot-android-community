package io.paritytech.polkadotapp.feature_products_impl.domain

import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductRepository
import javax.inject.Inject

class ProductListInteractor @Inject constructor(
    private val productRepository: ProductRepository
) {
    fun observeProducts() = productRepository.observeProducts()
}
