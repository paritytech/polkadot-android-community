package io.paritytech.polkadotapp.feature_sso_impl.domain.session.model

import io.paritytech.polkadotapp.bandersnatch_crypto.ContextualAlias
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocationOutcome
import io.paritytech.polkadotapp.feature_products_api.model.signing.SignedTransaction
import java.util.UUID

class SsoSessionResponse private constructor(
    val sessionId: SsoSessionId,
    val respondingTo: SsoSessionRequestId,
    val ownRequestId: SsoSessionRequestId,
    val content: Content
) {
    companion object {
        fun SsoSessionRequest.responseWith(content: Content): SsoSessionResponse {
            return SsoSessionResponse(
                sessionId = sessionId,
                respondingTo = requestId,
                ownRequestId = UUID.randomUUID().toString(),
                content = content
            )
        }
    }

    sealed class Content {
        class SignedPayload(val signed: SignedTransaction.WithDedicatedSignature) : Content()

        class FailedToSignTransaction(val error: String) : Content()

        class SignedGeneralTransaction(val signedTx: DataByteArray) : Content()

        class FailedToCreateTransaction(val error: String) : Content()

        class AliasResult(val alias: ContextualAlias) : Content()

        class FailedToGetAlias(val error: String) : Content()

        class ResourceAllocationResult(val outcomes: List<ApAllocationOutcome>) : Content()

        class FailedToAllocateResources(val error: String) : Content()
    }
}
