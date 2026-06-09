package io.paritytech.polkadotapp.feature_sso_impl.domain.signTransaction

import io.paritytech.polkadotapp.feature_products_api.model.signing.SignedTransaction
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContext
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody
import io.paritytech.polkadotapp.feature_sso_impl.domain.SsoService
import io.paritytech.polkadotapp.feature_sso_impl.domain.model.SsoSessionData
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionRequest
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionResponse
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionResponse.Companion.responseWith
import timber.log.Timber

class SsoSigningContext(
    sessionData: SsoSessionData,
    private val request: SsoSessionRequest,
    private val ssoService: SsoService,
    override val signingRequestBody: SigningRequestBody,
) : SigningContext {
    override val requesterName: String = sessionData.name
    override val requesterIconUrl: String = sessionData.icon

    override suspend fun deliverSignedResult(signedTransaction: SignedTransaction): Result<Unit> {
        Timber.d("Delivering signed result to $requesterName")
        val responseContent = when (signedTransaction) {
            is SignedTransaction.GeneralTransaction -> SsoSessionResponse.Content.SignedGeneralTransaction(signedTransaction.signedTx)
            is SignedTransaction.WithDedicatedSignature -> SsoSessionResponse.Content.SignedPayload(signedTransaction)
        }
        val response = request.responseWith(responseContent)
        return ssoService.sendResponse(response)
            .onSuccess { Timber.d("Signed result delivered to $requesterName") }
            .onFailure { Timber.e(it, "Failed to deliver signed result to $requesterName") }
    }

    override suspend fun deliverRejection(): Result<Unit> {
        Timber.d("Delivering rejection to $requesterName")
        val responseContent = when (signingRequestBody) {
            is SigningRequestBody.Transaction, is SigningRequestBody.Raw -> SsoSessionResponse.Content.FailedToSignTransaction("Rejected")
            is SigningRequestBody.CreateTransaction -> SsoSessionResponse.Content.FailedToCreateTransaction("Rejected")
        }
        val response = request.responseWith(responseContent)
        return ssoService.sendResponse(response)
            .onSuccess { Timber.d("Rejection delivered to $requesterName") }
            .onFailure { Timber.e(it, "Failed to deliver rejection to $requesterName") }
    }
}
