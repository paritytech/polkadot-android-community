package io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.states

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import io.paritytech.polkadotapp.common.utils.stateMachine.StateMachine
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.PendingMessageReplacementPolicy
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.PendingMessageReplacementPolicy.Decision
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.models.StatementTransportEvent
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.CommunicationSideEffect
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.sessions.stateMachine.CommunicationStateEvent
import java.util.UUID

// topic size (32 byte) + channel size (32 byte) + expiry (8 byte) + proof (64 byte) + signer (32 byte)
private val STATEMENT_OVERHEAD = (32 + 32 + 8 + 64 + 32).bytes

internal val PROTOCOL_ACK_CODE: UByte = 0u

sealed class CommunicationState : StateMachine.State<CommunicationState, CommunicationSideEffect, CommunicationStateEvent> {
    abstract val maxStatementSize: InformationSize
    abstract val canCompact: Boolean
    abstract val replacementPolicy: PendingMessageReplacementPolicy

    protected val maxRequestSize: InformationSize get() = maxStatementSize - STATEMENT_OVERHEAD

    private val maxRequestSizeBytes: Long get() = maxRequestSize.inWholeBytes

    protected fun splitMessagesToFitRequest(messages: List<EncodedMessage>): MessagesSplit {
        val fitsInRequest = mutableListOf<EncodedMessage>()
        var currentSize = 0L

        for ((index, message) in messages.withIndex()) {
            if (currentSize + message.size <= maxRequestSizeBytes) {
                fitsInRequest.add(message)
                currentSize += message.size
            } else {
                val remaining = messages.subList(index, messages.size)
                return MessagesSplit(fitsInRequest, remaining)
            }
        }

        return MessagesSplit(fitsInRequest, emptyList())
    }

    protected fun generateNewRequestId(): String = UUID.randomUUID().toString()

    protected fun checkAlreadyPendingMessage(
        message: EncodedMessage,
        pendingMessages: List<EncodedMessage>,
        outgoingPendingRequest: StatementTransportEvent.Request?
    ): Boolean {
        if (pendingMessages.any { it.contentEquals(message) }) {
            return true
        }

        if (outgoingPendingRequest != null) {
            if (outgoingPendingRequest.messages.any { it.contentEquals(message) }) {
                return true
            }
        }

        return false
    }

    protected fun checkSizeLimitExceeded(message: EncodedMessage): Boolean {
        return message.size > maxRequestSizeBytes
    }

    protected fun decideAgainstPending(message: EncodedMessage, pendingMessages: List<EncodedMessage>): PendingMessageOutcome {
        if (!replacementPolicy.isReplaceable(message)) return PendingMessageOutcome.Add

        val decisions = pendingMessages.map { pendingMessage -> pendingMessage to replacementPolicy.decide(message, pendingMessage) }
        if (decisions.any { (_, decision) -> decision == Decision.DROP_NEW }) return PendingMessageOutcome.Drop

        val replaced = decisions.firstOrNull { (_, decision) -> decision == Decision.REPLACE_PENDING }?.first
        return if (replaced != null) PendingMessageOutcome.Replace(replaced) else PendingMessageOutcome.Add
    }

    protected fun List<EncodedMessage>.replaceMessage(replaced: EncodedMessage, message: EncodedMessage): List<EncodedMessage> {
        return map { if (it.contentEquals(replaced)) message else it }
    }

    protected sealed interface PendingMessageOutcome {
        object Add : PendingMessageOutcome
        object Drop : PendingMessageOutcome
        class Replace(val pendingMessage: EncodedMessage) : PendingMessageOutcome
    }

    protected data class MessagesSplit(
        val fitsInRequest: List<EncodedMessage>,
        val remaining: List<EncodedMessage>
    )
}
