package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId

data class MessagePosition(val sortOrder: Long, val timestamp: Timestamp) : Comparable<MessagePosition> {
    override fun compareTo(other: MessagePosition): Int {
        return compareValuesBy(this, other, MessagePosition::sortOrder, MessagePosition::timestamp)
    }
}

data class LastSeenIncomingMessage(val id: ChatMessageId, val position: MessagePosition)
