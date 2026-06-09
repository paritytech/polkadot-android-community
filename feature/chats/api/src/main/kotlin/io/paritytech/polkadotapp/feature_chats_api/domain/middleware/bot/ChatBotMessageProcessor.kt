package io.paritytech.polkadotapp.feature_chats_api.domain.middleware.bot

import io.paritytech.polkadotapp.feature_chats_api.domain.model.filterCustomContents

interface ChatBotMessageProcessor {
    context(ChatBotContext)
    fun launchSendingMessages()
}

context(ChatBotContext)
suspend inline fun <reified T> messageWasSent(): Boolean {
    return getPersistedMessages()
        .filterCustomContents<T>()
        .isNotEmpty()
}
