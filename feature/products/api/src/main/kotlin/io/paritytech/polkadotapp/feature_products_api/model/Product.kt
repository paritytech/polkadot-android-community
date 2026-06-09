package io.paritytech.polkadotapp.feature_products_api.model

import io.paritytech.polkadotapp.common.utils.Identifiable

// It is important for Product to be a proper data-class since it is used in product extension provider for diffing
data class Product(
    val id: ProductId,
    val name: String,
    @Deprecated("Used only for debug menu. Will be migrated to ProductIntegration metadata.")
    val scriptUrl: String,
    val contentHash: String?,
) : Identifiable {
    override val identifier: String get() = id.value
}
