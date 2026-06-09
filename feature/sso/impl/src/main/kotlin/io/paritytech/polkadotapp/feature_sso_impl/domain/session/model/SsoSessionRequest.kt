package io.paritytech.polkadotapp.feature_sso_impl.domain.session.model

import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.OnExistingAllowancePolicy
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody

typealias SsoSessionRequestId = String

class SsoSessionRequest(
    val sessionId: SsoSessionId,
    val requestId: SsoSessionRequestId,
    val content: Content
) {
    sealed class Content {
        data object Disconnected : Content()

        class SigningRequest(val request: SigningRequestBody.ResultHasSignature) : Content()

        class CreateTransactionRequest(val request: SigningRequestBody.CreateTransaction) : Content()

        class AliasRequest(
            val productAccountId: ProductAccountId,
            val productDotNsIdentifier: String,
        ) : Content()

        class ResourceAllocationRequest(
            val callingProduct: ProductId,
            val resources: List<ApAllocatableResource>,
            val onExisting: OnExistingAllowancePolicy,
        ) : Content()
    }
}
