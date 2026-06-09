package io.paritytech.polkadotapp.feature_products_api.model.signing

import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.signing.createTransaction.TxPayload

sealed interface SigningRequestBody {
    val account: ProductAccountId

    sealed interface ResultHasSignature : SigningRequestBody

    class Transaction(val payload: SignerPayloadJson) : ResultHasSignature {
        override val account: ProductAccountId get() = payload.account
    }

    class Raw(val payload: SigningRawPayload) : ResultHasSignature {
        override val account: ProductAccountId get() = payload.account
    }

    class CreateTransaction(val payload: TxPayload<ProductAccountId>) : SigningRequestBody {
        override val account: ProductAccountId get() = payload.signer
    }
}
