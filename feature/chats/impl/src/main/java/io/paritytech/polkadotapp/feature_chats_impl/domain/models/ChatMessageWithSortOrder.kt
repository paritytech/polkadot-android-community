package io.paritytech.polkadotapp.feature_chats_impl.domain.models

import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage

class ChatMessageWithSortOrder(
    val message: ChatMessage,
    val sortOrder: Long,
)
