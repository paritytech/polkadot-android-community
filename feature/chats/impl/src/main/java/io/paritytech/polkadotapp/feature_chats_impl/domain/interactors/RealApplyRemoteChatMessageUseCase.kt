package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplyRemoteChatMessageUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplyRemoteChatMessageUseCase.MessageForSync
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplyRemoteChatMessageUseCase.MessagePageForSync
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageOrigin
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageReaction
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageSyncCursor
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatVariant
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onEdited
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onReacted
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onReactionRemoved
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onTokenContent
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toChatMessageOrUnsupported
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toEncodedMessage
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.AlwaysFailCustomContentDecoder
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ProcessedChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.ChatEngine
import io.paritytech.polkadotapp.feature_chats_impl.domain.ChatMessagePlacement
import io.paritytech.polkadotapp.feature_chats_impl.domain.ChatMessageSaveConflictStrategy
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.time.Instant

class RealApplyRemoteChatMessageUseCase @Inject constructor(
    private val chatMessageRepository: ChatMessageRepository,
    private val contactsRepository: ContactsRepository,
    private val chatEngine: ChatEngine,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
    private val processedChatMessageRepository: ProcessedChatMessageRepository,
) : ApplyRemoteChatMessageUseCase {
    // Sync transports bytes; Custom renderers aren't needed here.
    private val customContentDecoder = AlwaysFailCustomContentDecoder()

    override suspend fun apply(
        encoded: ByteArray,
        peerAccountId: AccountId,
        isOutgoing: Boolean,
        status: ChatMessage.Status,
    ) {
        val authorAccountId = if (isOutgoing) ourAccountId() else peerAccountId
        val chatMessage = encoded.toChatMessageOrUnsupported(
            authorAccountId = authorAccountId,
            contactAccountId = peerAccountId,
            messageStatus = status,
        )
            .logFailure("ApplyRemoteChatMessageUseCase: failed to decode remote/synced message; dropping")
            .getOrElse { return }

        val isSavedAsNew = chatEngine.saveMessage(chatMessage, ChatMessageSaveConflictStrategy.IGNORE, ChatMessagePlacement.ByTimestamp)

        if (!isSavedAsNew) {
            chatMessageRepository.updateMessageStatus(chatMessage.id, status)
        }

        val mustProcess = processedChatMessageRepository.tryMarkProcessed(chatMessage.id)
        if (mustProcess) {
            processChatMessages(peerAccountId, isOutgoing, chatMessage)
        }
    }

    private suspend fun processChatMessages(
        peerAccountId: AccountId,
        isOutgoing: Boolean,
        chatMessage: ChatMessage
    ) {
        val chatId = ChatId.fromContact(peerAccountId)
        chatMessage
            .onEdited { edited ->
                chatEngine.saveRevision(
                    messageId = edited.messageId,
                    content = edited.content,
                    chatId = chatId,
                    timestamp = chatMessage.timestamp,
                )
            }
            .onReacted { reacted ->
                chatMessageRepository.addReaction(
                    reaction = ChatMessageReaction(
                        messageId = reacted.messageId,
                        content = reacted.content,
                        origin = chatMessage.origin,
                        timestamp = chatMessage.timestamp,
                    ),
                    chatId = chatId,
                )
            }
            .onReactionRemoved { removed ->
                chatMessageRepository.removeMessageReaction(
                    reaction = ChatMessageReaction(
                        messageId = removed.messageId,
                        content = removed.content,
                        origin = chatMessage.origin,
                        timestamp = chatMessage.timestamp,
                    ),
                    chatId = chatId,
                )
            }
            .onTokenContent { token ->
                if (!isOutgoing) {
                    if (token.isVoIP) {
                        contactsRepository.updateContactVoipPushToken(peerAccountId, token.token)
                    } else {
                        contactsRepository.updateContactPushToken(peerAccountId, token.token, token.operatingSystem)
                    }
                }
            }
    }

    override fun observeLocalMessageChanges(): Flow<Unit> = chatEngine.observeMessagesChanged()

    override suspend fun getMessagesUpdatedAfter(after: Instant): List<MessageForSync> {
        return chatMessageRepository.getMessagesUpdatedAfter(after, customContentDecoder).mapNotNull { it.toMessageForSync() }
    }

    override suspend fun getMessagePageForSync(
        updatedAfter: Instant,
        updatedUpTo: Instant?,
        after: ChatMessageSyncCursor?,
        limit: Int,
    ): MessagePageForSync {
        val messages = chatMessageRepository.getMessagesUpdatedInRange(updatedAfter, updatedUpTo, after, limit, customContentDecoder)
        val nextCursor = messages.lastOrNull()
            ?.takeIf { messages.size == limit }
            ?.let { ChatMessageSyncCursor(timestamp = it.timestamp, messageId = it.id) }

        return MessagePageForSync(messages.mapNotNull { it.toMessageForSync() }, nextCursor)
    }

    override suspend fun countMessagesForSync(updatedAfter: Instant, updatedUpTo: Instant?): Int {
        return chatMessageRepository.countMessagesUpdatedInRange(updatedAfter, updatedUpTo)
    }

    private fun ChatMessage.toMessageForSync(): MessageForSync? {
        val syncable = toSyncableMessage() ?: return null
        val contact = syncable.chatId.chatVariant() as? ChatVariant.Contact ?: return null

        return syncable.toEncodedMessage()
            .map { encoded ->
                MessageForSync(
                    encoded = encoded,
                    peerAccountId = contact.contactAccountId,
                    isOutgoing = syncable.origin is ChatMessageOrigin.User,
                    status = syncable.status,
                    timestamp = syncable.timestamp,
                )
            }
            .logFailure("RealApplyRemoteChatMessageUseCase: failed to map message $id for sync")
            .getOrNull()
    }

    private fun ChatMessage.toSyncableMessage(): ChatMessage? = when (val content = content) {
        is ChatMessage.Content.ChatRequest -> content.welcome?.let { this.copy(content = it) }
        else -> this
    }

    private suspend fun ourAccountId(): AccountId {
        return accountRepository.getWalletAccount().accountIdIn(chainRegistry.getChain(knownChains.people))
    }
}
