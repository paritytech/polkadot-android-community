package io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.states

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.stateMachine.StateMachine
import io.paritytech.polkadotapp.feature_statement_store_api.domain.IsResponded
import io.paritytech.polkadotapp.feature_statement_store_api.domain.RequestId
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.models.StatementTransportEvent
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.CommunicationSideEffect
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.CommunicationStateEvent
import kotlin.collections.orEmpty

class Initialization(
    private val pendingMessages: List<EncodedMessage>,
    override val maxStatementSize: InformationSize,
) : CommunicationState() {
    context(StateMachine.Transition<CommunicationState, CommunicationSideEffect>)
    override suspend fun bootstrap() {
        emitSideEffect(CommunicationSideEffect.FetchInitialData)
    }

    context(StateMachine.Transition<CommunicationState, CommunicationSideEffect>)
    override suspend fun performTransition(event: CommunicationStateEvent) {
        when (event) {
            is CommunicationStateEvent.SubmitMessage -> {
                if (checkSizeLimitExceeded(event.message)) {
                    emitSideEffect(CommunicationSideEffect.NotifyMessageTooLarge(event.message, maxRequestSize))
                    return
                }

                if (checkAlreadyPendingMessage(event.message, pendingMessages, null)) return

                emitState(
                    Initialization(
                        pendingMessages = pendingMessages + event.message,
                        maxStatementSize = maxStatementSize
                    )
                )
            }

            is CommunicationStateEvent.InitialDataFetched -> {
                event.incomingRequests.forEach { (request, isResponded) ->
                    if (!isResponded) {
                        emitSideEffect(CommunicationSideEffect.RequestReceived(request))
                    }
                }

                val incomingRequests = event.incomingRequests.mapKeys { (request, _) -> request.requestId }

                if (pendingMessages.isEmpty()) {
                    emitState(
                        Active(
                            outgoingPendingRequest = event.outgoingPendingRequest,
                            incomingRequests = incomingRequests,
                            pendingMessages = emptyList(),
                            expiry = event.lastUsedExpiry,
                            maxStatementSize = maxStatementSize
                        )
                    )

                    emitSideEffect(CommunicationSideEffect.StartPolling)
                    return
                }

                val alreadySentMessages = event.outgoingPendingRequest?.messages.orEmpty()
                val combinedMessages = alreadySentMessages + pendingMessages
                val split = splitMessagesToFitRequest(combinedMessages)

                val shouldCreateNewRequest = split.fitsInRequest.size > alreadySentMessages.size

                if (shouldCreateNewRequest) {
                    createAndSubmitNewRequest(event, incomingRequests, split)
                } else {
                    emitState(
                        Active(
                            outgoingPendingRequest = event.outgoingPendingRequest,
                            incomingRequests = incomingRequests,
                            pendingMessages = pendingMessages,
                            expiry = event.lastUsedExpiry,
                            maxStatementSize = maxStatementSize
                        )
                    )
                }

                emitSideEffect(CommunicationSideEffect.StartPolling)
            }

            is CommunicationStateEvent.RequestSent -> Unit
            is CommunicationStateEvent.ResponseSent -> Unit
            is CommunicationStateEvent.SubmitResponse -> Unit
            is CommunicationStateEvent.ResponseReceived -> Unit
            is CommunicationStateEvent.RequestReceived -> Unit
            is CommunicationStateEvent.InvalidateSession -> Unit
        }
    }

    context(StateMachine.Transition<CommunicationState, CommunicationSideEffect>)
    private suspend fun createAndSubmitNewRequest(
        event: CommunicationStateEvent.InitialDataFetched,
        incomingRequests: Map<RequestId, IsResponded>,
        split: MessagesSplit
    ) {
        val newExpiry = event.lastUsedExpiry.inc()
        val newRequest = StatementTransportEvent.Request(
            requestId = generateNewRequestId(),
            expiry = newExpiry,
            messages = split.fitsInRequest
        )

        emitState(
            Active(
                outgoingPendingRequest = newRequest,
                incomingRequests = incomingRequests,
                pendingMessages = split.remaining,
                expiry = newExpiry,
                maxStatementSize = maxStatementSize
            )
        )

        emitSideEffect(
            CommunicationSideEffect.SubmitRequest(newRequest)
        )
    }
}
