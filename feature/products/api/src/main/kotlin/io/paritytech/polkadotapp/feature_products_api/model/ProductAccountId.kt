package io.paritytech.polkadotapp.feature_products_api.model

typealias ProductDerivationIndex = Int

data class ProductAccountId(val productId: String, val derivationIndex: ProductDerivationIndex)
