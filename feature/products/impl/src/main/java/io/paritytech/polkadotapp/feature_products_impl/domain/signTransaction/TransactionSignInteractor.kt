package io.paritytech.polkadotapp.feature_products_impl.domain.signTransaction

import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.signing.SignedTransaction

interface TransactionSignInteractor {
    val account: ProductAccountId

    suspend fun parseSigningContent(): Result<ParsedSigningContent>

    suspend fun humanReadableRepresentation(): Result<String>

    suspend fun sign(): Result<SignedTransaction>
}
