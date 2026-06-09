package io.paritytech.polkadotapp.feature_products_impl.domain.product

import io.paritytech.polkadotapp.feature_products_api.model.ProductId

/**
 * Resolves the worker script content for a product's chat/background integration.
 *
 * Different implementations support different resolution strategies:
 * - [ArchiveScriptResolver]: extracts `/worker/index.js` from the SPA archive
 * - Future: subdomain resolution (`worker.product.dot`) or manifest-based lookup
 */
interface ProductScriptResolver {
    suspend fun resolveScript(productId: ProductId): Result<String>

    /**
     * Checks whether a worker script can be resolved for the given product
     * without reading its content. Implementations may optimize this
     * (e.g. file existence check only).
     */
    suspend fun canResolveScript(productId: ProductId): Boolean
}
