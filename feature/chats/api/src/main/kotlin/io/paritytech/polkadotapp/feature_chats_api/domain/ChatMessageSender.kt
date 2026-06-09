package io.paritytech.polkadotapp.feature_chats_api.domain

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import java.util.UUID

interface ChatMessageSender {
    suspend fun sendUserMessage(
        messageId: ChatMessageId = UUID.randomUUID().toString(),
        chatId: ChatId,
        content: ChatMessage.Content,
        replyToMessageId: String? = null,
    ): ChatMessage

    context(ComputationalScope)
    fun startExtensions()
}
