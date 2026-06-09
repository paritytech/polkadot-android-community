package io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.states

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.stateMachine.StateMachine
import io.paritytech.polkadotapp.feature_statement_store_api.domain.IsResponded
import io.paritytech.polkadotapp.feature_statement_store_api.domain.NOT_RESPONDED
import io.paritytech.polkadotapp.feature_statement_store_api.domain.RESPONDED
import io.paritytech.polkadotapp.feature_statement_store_api.domain.RequestId
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.StatementExpiry
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.models.StatementTransportEvent
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.CommunicationSideEffect
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.CommunicationStateEvent
import timber.log.Timber
import kotlin.collections.isNotEmpty

data class Active(
    private val outgoingPendingRequest: StatementTransportEvent.Request?,
    private val incomingRequests: Map<RequestId, IsResponded>,
    private val pendingMessages: List<EncodedMessage>,
    private val expiry: ULong,
    override val maxStatementSize: InformationSize,
) : CommunicationState() {
    context(StateMachine.Transition<CommunicationState, CommunicationSideEffect>)
    override suspend fun performTransition(event: CommunicationStateEvent) {
        when (event) {
            is CommunicationStateEvent.InitialDataFetched -> Unit

            is CommunicationStateEvent.SubmitMessage -> {
                if (checkSizeLimitExceeded(event.message)) {
                    emitSideEffect(CommunicationSideEffect.NotifyMessageTooLarge(event.message, maxRequestSize))
                    return
                }

                if (checkAlreadyPendingMessage(event.message, pendingMessages, outgoingPendingRequest)) return

                if (pendingMessages.isEmpty()) {
                    if (outgoingPendingRequest == null) {
                        submitRequestWithNewMessage(event.message)
                    } else {
                        tryAppendMessageToOutgoingRequest(outgoingPendingRequest.messages, event.message)
                    }
                } else {
                    emitState(
                        copy(pendingMessages = pendingMessages + event.message)
                    )
                }
            }

            is CommunicationStateEvent.SubmitResponse -> {
                if (incomingRequests.isEmpty()) {
                    Timber.e("No incoming request to respond to")
                    return
                }
                if (!incomingRequests.contains(event.toRequestId)) {
                    Timber.e("No incoming request to respond to")
                    return
                }
                val isRequestAlreadyResponded = incomingRequests.getValue(event.toRequestId)

                if (isRequestAlreadyResponded) {
                    Timber.e("No incoming request to respond to")
                    return
                }
                val newExpiry = StatementExpiry.nextAfter(expiry)

                emitState(
                    copy(
                        expiry = newExpiry,
                        incomingRequests = incomingRequests.plus(event.toRequestId to RESPONDED)
                    )
                )

                emitSideEffect(
                    CommunicationSideEffect.SubmitResponse(
                        StatementTransportEvent.Response(
                            requestId = event.toRequestId,
                            expiry = newExpiry,
                            responseCode = event.responseCode
                        )
                    )
                )
            }

            is CommunicationStateEvent.RequestReceived -> {
                if (incomingRequests.contains(event.request.requestId)) return

                emitState(
                    copy(
                        incomingRequests = incomingRequests.plus(event.request.requestId to NOT_RESPONDED)
                    )
                )
                emitSideEffect(
                    CommunicationSideEffect.RequestReceived(event.request)
                )
            }

            is CommunicationStateEvent.ResponseReceived -> {
                if (event.response.requestId == outgoingPendingRequest?.requestId) {
                    emitSideEffect(
                        CommunicationSideEffect.ResponseReceived(
                            event.response.responseCode,
                            outgoingPendingRequest.messages
                        )
                    )

                    if (pendingMessages.isNotEmpty()) {
                        val newExpiry = StatementExpiry.nextAfter(expiry)

                        val split = splitMessagesToFitRequest(pendingMessages)

                        val request = StatementTransportEvent.Request(
                            requestId = generateNewRequestId(),
                            expiry = newExpiry,
                            messages = split.fitsInRequest
                        )

                        emitState(
                            copy(
                                outgoingPendingRequest = request,
                                expiry = newExpiry,
                                pendingMessages = split.remaining
                            )
                        )

                        emitSideEffect(
                            CommunicationSideEffect.SubmitRequest(request)
                        )
                    } else {
                        emitState(
                            copy(outgoingPendingRequest = null)
                        )
                    }
                }
            }

            is CommunicationStateEvent.RequestSent -> {
            }

            is CommunicationStateEvent.ResponseSent -> {
            }

            is CommunicationStateEvent.InvalidateSession -> {
                emitSideEffect(CommunicationSideEffect.StopPolling)
                emitState(Initialization(pendingMessages, maxStatementSize))
                emitSideEffect(CommunicationSideEffect.FetchInitialData)
            }
        }
    }

    context(StateMachine.Transition<CommunicationState, CommunicationSideEffect>)
    private suspend fun tryAppendMessageToOutgoingRequest(
        currentMessages: List<EncodedMessage>,
        newMessage: EncodedMessage
    ) {
        assert(pendingMessages.isEmpty())

        val split = splitMessagesToFitRequest(currentMessages + newMessage)
        val hasAddedNewMessagesToRequest = split.fitsInRequest.size > currentMessages.size

        if (hasAddedNewMessagesToRequest) {
            val newExpiry = StatementExpiry.nextAfter(expiry)

            val request = StatementTransportEvent.Request(
                requestId = generateNewRequestId(),
                expiry = newExpiry,
                messages = split.fitsInRequest
            )

            emitState(
                copy(
                    outgoingPendingRequest = request,
                    expiry = newExpiry,
                    pendingMessages = split.remaining
                )
            )

            emitSideEffect(
                CommunicationSideEffect.SubmitRequest(request)
            )
        } else {
            emitState(
                copy(pendingMessages = listOf(newMessage))
            )
        }
    }

    context(StateMachine.Transition<CommunicationState, CommunicationSideEffect>)
    private suspend fun submitRequestWithNewMessage(message: EncodedMessage) {
        assert(pendingMessages.isEmpty())

        val newExpiry = StatementExpiry.nextAfter(expiry)

        val request = StatementTransportEvent.Request(
            requestId = generateNewRequestId(),
            expiry = newExpiry,
            messages = listOf(message)
        )

        emitState(
            copy(
                outgoingPendingRequest = request,
                expiry = newExpiry,
                pendingMessages = emptyList()
            )
        )

        emitSideEffect(
            CommunicationSideEffect.SubmitRequest(request)
        )
    }
}
