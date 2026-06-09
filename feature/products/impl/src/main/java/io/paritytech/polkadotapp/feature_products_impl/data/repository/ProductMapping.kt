package io.paritytech.polkadotapp.feature_products_impl.data.repository

import io.paritytech.polkadotapp.database.model.ProductLocal
import io.paritytech.polkadotapp.feature_products_api.model.Product
import io.paritytech.polkadotapp.feature_products_api.model.ProductId

internal fun ProductLocal.toProduct(): Product {
    return Product(
        id = ProductId.fromStoredValue(id),
        name = name,
        scriptUrl = scriptUrl,
        contentHash = contentHash
    )
}
