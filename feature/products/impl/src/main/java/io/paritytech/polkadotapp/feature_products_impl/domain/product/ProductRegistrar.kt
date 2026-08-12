package io.paritytech.polkadotapp.feature_products_impl.domain.product

import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for registering products on first visit.
 * Idempotent — no-op if the product already exists.
 */
interface ProductRegistrar {
    suspend fun ensureRegistered(productId: ProductId, contentHash: String?)
}

context(scope: CoroutineScope)
fun ProductRegistrar.launchEnsureRegistered(productId: ProductId, contentHash: String?) = scope.launchUnit {
    ensureRegistered(productId, contentHash)
}

@Singleton
class RealProductRegistrar @Inject constructor(
    private val productRepository: ProductRepository,
    private val productIconResolver: ProductIconResolver,
    dispatchers: CoroutineDispatchers,
) : ProductRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override suspend fun ensureRegistered(productId: ProductId, contentHash: String?) {
        val existing = productRepository.getProductById(productId)
        if (existing != null) {
            if (contentHash != null) {
                productRepository.updateContentHash(productId, contentHash)
            }
            if (existing.iconUrl == null) prefetchIcon(productId)
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

        prefetchIcon(productId)
    }

    // As soon as a product is known, resolve its icon in the background and persist it, so it shows up
    // immediately on later sessions without re-resolving.
    private fun prefetchIcon(productId: ProductId) {
        scope.launch {
            val iconUrl = productIconResolver.resolveIconUrl(productId) ?: return@launch
            productRepository.updateIcon(productId, iconUrl)
        }
    }
}
