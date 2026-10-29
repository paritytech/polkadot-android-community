package io.paritytech.polkadotapp.feature_chats_impl.domain.seen

import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId

data class SeenWatermarkState(
    val messageId: ChatMessageId,
    val order: Long,
    val timestamp: Timestamp,
    val lastSentOrder: Long,
    val lastSentTimestamp: Timestamp,
)
