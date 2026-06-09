package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions

import io.paritytech.polkadotapp.common.data.os.OperatingSystem
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatPushId
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatPushToken
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageReaction
import io.paritytech.polkadotapp.feature_chats_api.domain.model.HopTicket

interface ChatSessionCallbacks {
    suspend fun onChatTokenReceived(accountId: AccountId, token: ChatPushToken, operatingSystem: OperatingSystem, isVoIP: Boolean)

    suspend fun onMessageReaction(
        reaction: ChatMessageReaction,
        chatId: ChatId
    )

    suspend fun onMessageReactionRemoved(
        reaction: ChatMessageReaction,
        chatId: ChatId
    )

    suspend fun onShouldNotifyNewMessageSent(
        messageId: ChatMessageId,
        accountId: AccountId,
        pushId: ChatPushId,
        encryptedMessage: ByteArray,
        isVoIP: Boolean
    )

    suspend fun onPeerLeftChatReceived(accountId: AccountId)
    suspend fun onPeerAddedChatReceived(accountId: AccountId)

    suspend fun onIncomingCallReceived(chatId: ChatId, messageId: ChatMessageId, callerName: String, withVideo: Boolean)

    suspend fun onAttachmentReceived(chatId: ChatId, messageId: ChatMessageId, identifier: DataByteArray, ticket: HopTicket, nodeUrl: String, mimeType: String)
}
