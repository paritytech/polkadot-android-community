package io.paritytech.polkadotapp.feature_chats_impl.domain.models

import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId

data class LastReadMessage(
    val id: ChatMessageId,
    val timestamp: Long,
)
