package io.paritytech.polkadotapp.feature_chats_api.domain.model

import io.paritytech.polkadotapp.common.domain.model.Timestamp

data class ChatMessageSyncCursor(
    val timestamp: Timestamp,
    val messageId: ChatMessageId,
)
