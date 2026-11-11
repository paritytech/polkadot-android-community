package io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.states

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.stateMachine.StateMachine
import io.paritytech.polkadotapp.feature_statement_store_api.domain.RequestId
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.PendingMessageReplacementPolicy
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.StatementExpiry
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.models.StatementTransportEvent
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.CommunicationSideEffect
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.CommunicationStateEvent
import kotlin.collections.orEmpty

class Initialization(
    private val pendingMessages: List<EncodedMessage>,
    override val maxStatementSize: InformationSize,
    override val canCompact: Boolean,
    override val replacementPolicy: PendingMessageReplacementPolicy,
) : CommunicationState() {
    context(transition: StateMachine.Transition<CommunicationState, CommunicationSideEffect>)
    override suspend fun bootstrap() {
        transition.emitSideEffect(CommunicationSideEffect.FetchInitialData)
    }

    context(transition: StateMachine.Transition<CommunicationState, CommunicationSideEffect>)
    override suspend fun performTransition(event: CommunicationStateEvent) {
        when (event) {
            is CommunicationStateEvent.SubmitMessage -> {
                if (checkSizeLimitExceeded(event.message) && !canCompact) {
                    transition.emitSideEffect(CommunicationSideEffect.NotifyMessageTooLarge(event.message, maxRequestSize))
                    return
                }

                if (checkAlreadyPendingMessage(event.message, pendingMessages, null)) return

                val newPendingMessages = when (val outcome = decideAgainstPending(event.message, pendingMessages)) {
                    PendingMessageOutcome.Drop -> return
                    is PendingMessageOutcome.Replace -> pendingMessages.replaceMessage(outcome.pendingMessage, event.message)
                    PendingMessageOutcome.Add -> pendingMessages + event.message
                }

                transition.emitState(
                    Initialization(
                        pendingMessages = newPendingMessages,
                        maxStatementSize = maxStatementSize,
                        canCompact = canCompact,
                        replacementPolicy = replacementPolicy
                    )
                )
            }

            is CommunicationStateEvent.InitialDataFetched -> {
                var expiry = event.lastUsedExpiry
                val incomingRequests = mutableSetOf<RequestId>()

                event.incomingRequests.forEach { (request, isResponded) ->
                    if (!isResponded) {
                        expiry = StatementExpiry.nextAfter(expiry)

                        transition.emitSideEffect(CommunicationSideEffect.RequestReceived(request))
                        transition.emitSideEffect(
                            CommunicationSideEffect.SubmitResponse(
                                response = StatementTransportEvent.Response(
                                    requestId = request.requestId,
                                    expiry = expiry,
                                    responseCode = PROTOCOL_ACK_CODE
                                ),
                                request = request,
                            )
                        )
                    }

                    incomingRequests += request.requestId
                }

                if (pendingMessages.isEmpty()) {
                    transition.emitState(
                        activeState(event.outgoingPendingRequest, incomingRequests, emptyList(), expiry, isCompacting = false)
                    )

                    transition.emitSideEffect(CommunicationSideEffect.StartPolling)
                    return
                }

                val alreadySentMessages = event.outgoingPendingRequest?.messages.orEmpty()
                val combinedMessages = alreadySentMessages + pendingMessages
                val split = splitMessagesToFitRequest(combinedMessages)

                if (canCompact && split.remaining.isNotEmpty()) {
                    transition.emitState(
                        activeState(event.outgoingPendingRequest, incomingRequests, pendingMessages, expiry, isCompacting = true)
                    )
                    transition.emitSideEffect(CommunicationSideEffect.Compact(combinedMessages))
                    transition.emitSideEffect(CommunicationSideEffect.StartPolling)
                    return
                }

                val merge = mergeIntoRestoredRequest(alreadySentMessages)
                val mergedSplit = splitMessagesToFitRequest(merge.requestMessages + merge.pendingMessages)
                val shouldCreateNewRequest = merge.isRequestChanged || mergedSplit.fitsInRequest.size > alreadySentMessages.size

                if (shouldCreateNewRequest) {
                    createAndSubmitNewRequest(event.outgoingPendingRequest, incomingRequests, expiry, mergedSplit)
                } else {
                    transition.emitState(
                        activeState(event.outgoingPendingRequest, incomingRequests, merge.pendingMessages, expiry, isCompacting = false)
                    )
                }

                transition.emitSideEffect(CommunicationSideEffect.StartPolling)
            }

            is CommunicationStateEvent.RequestSent -> Unit
            is CommunicationStateEvent.ResponseSent -> Unit
            is CommunicationStateEvent.ResponseReceived -> Unit
            is CommunicationStateEvent.RequestReceived -> Unit
            is CommunicationStateEvent.CompactionCompleted -> Unit
            is CommunicationStateEvent.CompactionFailed -> Unit
            is CommunicationStateEvent.InvalidateSession -> Unit
        }
    }

    context(transition: StateMachine.Transition<CommunicationState, CommunicationSideEffect>)
    private suspend fun createAndSubmitNewRequest(
        outgoingPendingRequest: StatementTransportEvent.Request?,
        incomingRequests: Set<RequestId>,
        expiry: ULong,
        split: MessagesSplit
    ) {
        val newExpiry = StatementExpiry.nextAfter(expiry)
        val newRequest = StatementTransportEvent.Request(
            requestId = generateNewRequestId(),
            expiry = newExpiry,
            messages = split.fitsInRequest
        )

        transition.emitState(
            activeState(newRequest, incomingRequests, split.remaining, newExpiry, isCompacting = false)
        )

        transition.emitSideEffect(
            CommunicationSideEffect.SubmitRequest(newRequest)
        )
    }

    private fun mergeIntoRestoredRequest(restoredMessages: List<EncodedMessage>): RestoredRequestMerge {
        var requestMessages = restoredMessages
        var isRequestChanged = false
        val remainingPendingMessages = mutableListOf<EncodedMessage>()

        for (message in pendingMessages) {
            when (val outcome = decideAgainstPending(message, requestMessages)) {
                PendingMessageOutcome.Drop -> Unit
                is PendingMessageOutcome.Replace -> {
                    requestMessages = requestMessages.replaceMessage(outcome.pendingMessage, message)
                    isRequestChanged = true
                }
                PendingMessageOutcome.Add -> remainingPendingMessages += message
            }
        }

        return RestoredRequestMerge(requestMessages, remainingPendingMessages, isRequestChanged)
    }

    private class RestoredRequestMerge(
        val requestMessages: List<EncodedMessage>,
        val pendingMessages: List<EncodedMessage>,
        val isRequestChanged: Boolean,
    )

    private fun activeState(
        outgoingPendingRequest: StatementTransportEvent.Request?,
        incomingRequests: Set<RequestId>,
        pendingMessages: List<EncodedMessage>,
        expiry: ULong,
        isCompacting: Boolean
    ): Active {
        return Active(
            outgoingPendingRequest = outgoingPendingRequest,
            incomingRequests = incomingRequests,
            pendingMessages = pendingMessages,
            expiry = expiry,
            maxStatementSize = maxStatementSize,
            canCompact = canCompact,
            replacementPolicy = replacementPolicy,
            isCompacting = isCompacting
        )
    }
}
