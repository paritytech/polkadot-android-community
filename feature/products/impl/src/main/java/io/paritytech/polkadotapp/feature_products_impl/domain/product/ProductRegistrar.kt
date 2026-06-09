package io.paritytech.polkadotapp.feature_products_impl.domain.product

import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductRepository
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for registering products on first visit.
 * Idempotent — no-op if the product already exists.
 */
interface ProductRegistrar {
    suspend fun ensureRegistered(productId: ProductId, contentHash: String?)
}

context(CoroutineScope)
fun ProductRegistrar.launchEnsureRegistered(productId: ProductId, contentHash: String?) = launchUnit {
    ensureRegistered(productId, contentHash)
}

@Singleton
class RealProductRegistrar @Inject constructor(
    private val productRepository: ProductRepository,
) : ProductRegistrar {
    override suspend fun ensureRegistered(productId: ProductId, contentHash: String?) {
        val existing = productRepository.getProductById(productId)
        if (existing != null) {
            if (contentHash != null) {
                productRepository.updateContentHash(productId, contentHash)
            }
            return
        }

        productRepository.addProduct(
            id = productId,
            name = productId.value,
            scriptUrl = ""
        )

        if (contentHash != null) {
            productRepository.updateContentHash(productId, contentHash)
        }
    }
}
