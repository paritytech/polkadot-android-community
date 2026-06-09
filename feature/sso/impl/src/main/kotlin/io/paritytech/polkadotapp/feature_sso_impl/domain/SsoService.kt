package io.paritytech.polkadotapp.feature_sso_impl.domain

import io.paritytech.polkadotapp.common.utils.flowOfAll
import io.paritytech.polkadotapp.feature_products_api.domain.GetContextualAliasUseCase
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.AccountsProtocol
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContextHolder
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRouter
import io.paritytech.polkadotapp.feature_sso_impl.data.repository.SsoHandledRequestRepository
import io.paritytech.polkadotapp.feature_sso_impl.domain.model.SsoSessionData
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.SsoSessionManager
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionId
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionRequest
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionResponse
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionResponse.Companion.responseWith
import io.paritytech.polkadotapp.feature_sso_impl.domain.signTransaction.SsoSigningContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SsoService @Inject constructor(
    private val ssoSessionManager: SsoSessionManager,
    private val signingRouter: SigningRouter,
    private val signingContextHolder: SigningContextHolder,
    private val getContextualAliasUseCase: GetContextualAliasUseCase,
    private val accountsProtocol: AccountsProtocol,
    private val ssoHandledRequestRepository: SsoHandledRequestRepository,
) {
    fun watchSsoEvents(): Flow<SsoSessionRequest> {
        return flowOfAll {
            ssoSessionManager.init()

            ssoSessionManager.allMessages
        }
            .onEach { request -> handleRequest(request) }
    }

    suspend fun disconnectSession(sessionId: SsoSessionId) {
        ssoSessionManager.disconnectSession(sessionId)
    }

    suspend fun sendResponse(response: SsoSessionResponse): Result<Unit> {
        val session = ssoSessionManager.awaitSession(response.sessionId)
        return session.sendResponseAndAwaitSent(response)
    }

    // CommunicationSession may re-deliver requests that the other side never cleaned up, so we
    // de-duplicate by request id (persisted across restarts) and process each one at most once.
    private suspend fun handleRequest(request: SsoSessionRequest) {
        if (ssoHandledRequestRepository.wasHandled(request)) {
            Timber.d("Skipping already handled SSO request ${request.requestId}")
            return
        }

        val session = ssoSessionManager.getSession(request.sessionId)
        if (session == null) {
            Timber.w("Session id ${request.sessionId} was not found")
            return
        }

        val sessionName = session.sessionData.name

        when (val content = request.content) {
            is SsoSessionRequest.Content.Disconnected -> handleDisconnected(request, sessionName)
            is SsoSessionRequest.Content.SigningRequest -> handleSigningRequest(request, content.request, session.sessionData, sessionName)
            is SsoSessionRequest.Content.CreateTransactionRequest -> handleSigningRequest(request, content.request, session.sessionData, sessionName)
            is SsoSessionRequest.Content.AliasRequest -> handleAliasRequest(request, content, sessionName)
            is SsoSessionRequest.Content.ResourceAllocationRequest -> handleResourceAllocationRequest(request, content, sessionName)
        }

        ssoHandledRequestRepository.markHandled(request)
    }

    private suspend fun handleDisconnected(request: SsoSessionRequest, sessionName: String) {
        Timber.d("Disconnected received from $sessionName")
        ssoSessionManager.deleteSession(request.sessionId)
    }

    private suspend fun handleSigningRequest(
        request: SsoSessionRequest,
        signingRequest: SigningRequestBody,
        sessionData: SsoSessionData,
        sessionName: String,
    ) {
        Timber.d("SigningRequest received from $sessionName: account=${signingRequest.account}")

        val signingContext = SsoSigningContext(
            sessionData = sessionData,
            request = request,
            ssoService = this,
            signingRequestBody = signingRequest,
        )

        signingContextHolder.set(signingContext)
        signingRouter.openSignTransaction()
    }

    private suspend fun handleAliasRequest(
        request: SsoSessionRequest,
        content: SsoSessionRequest.Content.AliasRequest,
        sessionName: String,
    ) {
        Timber.d("AliasRequest received from $sessionName")

        val responseContent = getContextualAliasUseCase.getAlias(content.productAccountId).fold(
            onSuccess = { alias -> SsoSessionResponse.Content.AliasResult(alias) },
            onFailure = { error -> SsoSessionResponse.Content.FailedToGetAlias(error.message ?: "Unknown error") }
        )

        val response = request.responseWith(responseContent)
        sendResponse(response)
    }

    private suspend fun handleResourceAllocationRequest(
        request: SsoSessionRequest,
        content: SsoSessionRequest.Content.ResourceAllocationRequest,
        sessionName: String,
    ) {
        Timber.d("ResourceAllocationRequest received from $sessionName for ${content.callingProduct}")

        val responseContent = runCatching {
            accountsProtocol.requestResourceAllocation(
                callingProduct = content.callingProduct,
                resources = content.resources,
                onExisting = content.onExisting,
            )
        }.fold(
            onSuccess = { outcomes -> SsoSessionResponse.Content.ResourceAllocationResult(outcomes) },
            onFailure = { error -> SsoSessionResponse.Content.FailedToAllocateResources(error.message ?: "Unknown error") },
        )

        val response = request.responseWith(responseContent)
        sendResponse(response)
    }
}
