package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions

import io.paritytech.polkadotapp.common.data.os.OperatingSystem
import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.diffed
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatPushId
import io.paritytech.polkadotapp.feature_chats_api.domain.ContactChatSession
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageOrigin
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageReaction
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageReactionContent
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onAttachment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onContactAdded
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onDataChannelOffer
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onEdited
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onLeftChat
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onReacted
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onReactionRemoved
import io.paritytech.polkadotapp.feature_chats_api.domain.model.onTokenContent
import io.paritytech.polkadotapp.feature_chats_api.domain.username.FallbackUsernameGenerator
import io.paritytech.polkadotapp.feature_chats_impl.data.model.decodeAlwaysDecodableChatMessagePart
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toChatMessage
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toChatMessageOrUnsupported
import io.paritytech.polkadotapp.feature_chats_impl.data.model.toEncodedMessage
import io.paritytech.polkadotapp.feature_chats_impl.data.notifications.isDisplayableAsPush
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ProcessedChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.ChatEngine
import io.paritytech.polkadotapp.feature_chats_impl.domain.ChatMessageSaveConflictStrategy
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.ChatResponseCode
import io.paritytech.polkadotapp.feature_chats_impl.utils.ChatPushTokenUtils
import io.paritytech.polkadotapp.feature_statement_store_api.domain.CommunicationSession
import io.paritytech.polkadotapp.feature_statement_store_api.domain.RequestId
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.CommunicationSessionEvent
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import io.paritytech.polkadotapp.tools_push_notifications_api.NotifyType
import io.paritytech.polkadotapp.tools_push_notifications_api.PushRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.reflect.KClass

private val PUSH_ID_PREFIX = "notification".toByteArray(Charsets.UTF_8)

// Single source of truth for which content goes through the identity session: drives both routing
// (sessionFor) and the per-session SentMessagesNotFound reset, so they can't drift apart.
private val IDENTITY_SESSION_CONTENT_TYPES = listOf<KClass<out ChatMessage.Content>>(
    ChatMessage.Content.DeviceChatAccepted::class
)

class RealContactChatSession(
    scope: CoroutineScope,
    private val contact: Contact,
    private val communicationSessions: CommunicationSessions,
    private val chatMessageRepository: ChatMessageRepository,
    private val processedChatMessageRepository: ProcessedChatMessageRepository,
    private val callbacks: ChatSessionCallbacks,
    private val chatEngine: ChatEngine,
    private val fallbackUsernameGenerator: FallbackUsernameGenerator
) : ContactChatSession, CoroutineScope by scope {
    private val mainCommunicationSession get() = communicationSessions.main

    private val pushNotificationSession get() = communicationSessions.identity

    private val chatId = ChatId.fromContact(mainCommunicationSession.remoteAccount.accountId)

    override val incomingPushId: ChatPushId = pushNotificationSession
        .generateSharedIncomingSessionValue(PUSH_ID_PREFIX)
        .toDataByteArray()

    override val outgoingPushId: ChatPushId = pushNotificationSession
        .generateSharedOutgoingSessionValue(PUSH_ID_PREFIX)
        .toDataByteArray()

    val pushRules: List<PushRule> = listOf(
        PushRule(
            senderPubKey = pushNotificationSession.remoteAccount.accountId,
            topic = pushNotificationSession.incomingSessionId.toDataByteArray(),
            notifyType = NotifyType.ALERT
        )
    )

    init {
        communicationSessions.distinct.forEach(::subscribeSessionEvents)

        subscribeMessagesUpdates()

        launch { tryReparseUnsupportedMessages() }
    }

    override suspend fun sendToken(token: String) {
        val encoded = ChatPushTokenUtils.createAndroidToken(token)
        chatEngine.sendUserMessage(
            chatId = chatId,
            content = ChatMessage.Content.Token(encoded, OperatingSystem.ANDROID)
        )
    }

    override suspend fun sendLeftChatMessageAndAwait(): Result<Unit> {
        val message = ChatMessage.new(
            chatId = chatId,
            content = ChatMessage.Content.LeftChat,
            origin = ChatMessageOrigin.User,
            status = ChatMessage.Status.NEW
        )

        return message.toEncodedMessage()
            .flatMap { mainCommunicationSession.sendMessageAndAwait(it) }
            .logFailure("Could not send 'Left Chat' confirmation to peer.")
    }

    private fun subscribeMessagesUpdates() {
        chatEngine
            .subscribeOutgoingMessagesByStatus(chatId, ChatMessage.Status.NEW)
            .map { messages -> messages.filter(::shouldSend) }
            .diffed()
            .onEach { diff ->
                for (message in diff.added) {
                    trySendMessage(message)
                }
            }
            .launchIn(this)

        chatEngine
            .subscribeOutgoingMessagesByStatus(chatId, ChatMessage.Status.IS_SENT)
            .map { messages -> messages.filter(::shouldNotify) }
            .diffed()
            .onEach { diff ->
                for (message in diff.added) {
                    tryNotifyNewMessageSent(message)
                }
            }
            .launchIn(this)
    }

    private fun trySendMessage(message: ChatMessage) {
        val session = sessionFor(message)
        message.toEncodedMessage()
            .map { session.sendMessage(it) }
            .logFailure("Failed to send message: ${message.id}")
    }

    private fun sessionFor(message: ChatMessage): CommunicationSession {
        return if (message.content::class in IDENTITY_SESSION_CONTENT_TYPES) {
            communicationSessions.identity
        } else {
            communicationSessions.main
        }
    }

    private fun shouldSend(message: ChatMessage): Boolean {
        return message.content !is ChatMessage.Content.ChatRequest
    }

    private fun shouldNotify(message: ChatMessage): Boolean = message.content.isDisplayableAsPush()

    private fun subscribeSessionEvents(roledSession: RoledCommunicationSession) {
        val session = roledSession.session
        session
            .subscribeEvents()
            .onEach { event ->
                when (event) {
                    is CommunicationSessionEvent.MessageIsTooLarge -> {
                        handleMessageTooLarge(event.message)
                    }

                    is CommunicationSessionEvent.NewMessagesReceived -> {
                        handleNewMessagesReceived(session, event.requestId, event.messages)
                    }

                    is CommunicationSessionEvent.ResponseReceived -> {
                        handleResponseReceived(event.code, event.respondedMessages)
                    }

                    is CommunicationSessionEvent.MessagesSentSuccessfully -> {
                        handleMessagesSentSuccessfully(event.messages)
                    }

                    is CommunicationSessionEvent.SessionFailed -> {
                        // TODO: what should we do here?
                    }

                    is CommunicationSessionEvent.SentMessagesNotFound -> {
                        handleSentMessagesNotFound(roledSession.role)
                    }

                    is CommunicationSessionEvent.MessagesFailedToSend -> {
                        // Ignore. Separate job ensures message are always re-sent until delivered
                    }
                }
            }
            .launchIn(this)
    }

    private suspend fun handleNewMessagesReceived(source: CommunicationSession, requestId: RequestId, messages: List<EncodedMessage>) {
        Timber.d("handleNewMessagesReceived: ${messages.size} messages from ${contact.username}")

        val parsedMessages = messages.mapNotNull { encodedMessage ->
            encodedMessage.toChatMessageOrUnsupported(
                authorAccountId = source.remoteAccount.accountId,
                contactAccountId = source.remoteAccount.accountId,
                messageStatus = ChatMessage.Status.NEW
            ).getOrNull()
        }

        val messageIds = parsedMessages.map { it.id }

        chatEngine.saveMessages(parsedMessages, ChatMessageSaveConflictStrategy.IGNORE)

        val claimedIds = processedChatMessageRepository.tryMarkProcessed(messageIds)

        parsedMessages.forEach { message ->
            if (message.id in claimedIds) {
                Timber.i("Processing ${message.id} ${message.content::class.simpleName}")
                processParsedMessage(message)
            } else {
                Timber.i("Skipping ${message.id} (${message.content::class.simpleName}) - already processed")
            }
        }

        source.respond(requestId, ChatResponseCode.SUCCESS)
    }

    private suspend fun processParsedMessage(chatMessage: ChatMessage) = runCatching {
        val peerAccountId = mainCommunicationSession.remoteAccount.accountId
        chatMessage.onTokenContent { content ->
            Timber.d("processParsedMessage: received Token message from ${contact.username}, isVoIP=${content.isVoIP}, os=${content.operatingSystem}")
            callbacks.onChatTokenReceived(
                accountId = mainCommunicationSession.remoteAccount.accountId,
                token = content.token,
                operatingSystem = content.operatingSystem,
                isVoIP = content.isVoIP
            )
        }.onReacted {
            val reaction = createChatMessageReaction(it.messageId, it.content, chatMessage.timestamp)
            callbacks.onMessageReaction(reaction, chatId)
        }.onReactionRemoved {
            val reaction = createChatMessageReaction(it.messageId, it.content, chatMessage.timestamp)
            callbacks.onMessageReactionRemoved(reaction, chatId)
        }.onLeftChat {
            // Ignore a stale LEFT_CHAT from a prior session (reused topic) that predates this re-add.
            if (chatMessage.timestamp >= contact.addedAt.toEpochMilliseconds()) {
                callbacks.onPeerLeftChatReceived(peerAccountId)
            }
        }.onContactAdded {
            callbacks.onPeerAddedChatReceived(peerAccountId)
        }.onEdited { editedContent ->
            chatEngine.saveRevision(
                messageId = editedContent.messageId,
                content = editedContent.content,
                chatId = chatId,
                timestamp = chatMessage.timestamp
            )
        }.onDataChannelOffer { offer ->
            callbacks.onIncomingCallReceived(
                chatId = chatId,
                messageId = chatMessage.id,
                callerName = contact.username ?: fallbackUsernameGenerator.generateFromAccountId(contact.accountId),
                withVideo = offer.purpose == ChatMessage.Content.DataChannelOffer.Purpose.VIDEO_CALL,
            )
        }.onAttachment { attachment ->
            callbacks.onAttachmentReceived(
                chatId = chatId,
                messageId = chatMessage.id,
                identifier = attachment.identifier,
                ticket = attachment.ticket,
                nodeUrl = attachment.nodeUrl,
                mimeType = attachment.meta.mimeType
            )
        }
    }.logFailure(
        "Failed to process message ${chatMessage.id} " +
            "(content=${chatMessage.content::class.simpleName}, status=${chatMessage.status}) " +
            "from ${contact.username}"
    )

    private suspend fun tryReparseUnsupportedMessages() {
        val unsupportedMessages = chatMessageRepository.getUnsupportedMessages(chatId)

        for (unsupportedMessage in unsupportedMessages) {
            val rawContent = (unsupportedMessage.content as? ChatMessage.Content.Unsupported)?.rawContent
                ?: continue

            val parsedMessage = rawContent.toChatMessage(
                authorAccountId = mainCommunicationSession.remoteAccount.accountId,
                contactAccountId = mainCommunicationSession.remoteAccount.accountId,
                messageStatus = unsupportedMessage.status
            ).getOrNull() ?: continue

            // Explicitly using replace here since we actually want to overwrite content of previously non-parcelable message
            chatEngine.saveMessage(parsedMessage, ChatMessageSaveConflictStrategy.REPLACE)
            processParsedMessage(parsedMessage)
        }
    }

    private fun createChatMessageReaction(messageId: ChatMessageId, content: ChatMessageReactionContent, timestamp: Timestamp): ChatMessageReaction {
        return ChatMessageReaction(
            messageId = messageId,
            content = content,
            origin = ChatMessageOrigin.Contact(mainCommunicationSession.remoteAccount.accountId),
            timestamp = timestamp
        )
    }

    private suspend fun handleMessageTooLarge(encodedMessage: EncodedMessage) {
        val versionedMessage = encodedMessage.decodeAlwaysDecodableChatMessagePart().getOrNull() ?: return
        chatMessageRepository.removeMessage(versionedMessage.id)
    }

    private suspend fun handleResponseReceived(
        code: UByte,
        messages: List<EncodedMessage>
    ) {
        if (code == ChatResponseCode.SUCCESS) {
            for (encodedMessage in messages) {
                val versionedMessage = encodedMessage.decodeAlwaysDecodableChatMessagePart().getOrNull() ?: continue
                chatMessageRepository.updateMessageStatus(
                    messageId = versionedMessage.id,
                    status = ChatMessage.Status.IS_READ
                )
            }
        } else {
            // todo: handle error response code
        }
    }

    private suspend fun handleMessagesSentSuccessfully(
        messages: List<EncodedMessage>
    ) {
        for (encodedMessage in messages) {
            val messagePart = encodedMessage.decodeAlwaysDecodableChatMessagePart().getOrNull() ?: continue
            chatMessageRepository.updateMessageStatus(
                messagePart.id,
                ChatMessage.Status.IS_SENT
            )
        }
    }

    // A session's "not found" must reset only the messages routed to it (see sessionFor / the session's role).
    private suspend fun handleSentMessagesNotFound(role: ContactSessionRole) {
        when (role) {
            ContactSessionRole.Combined ->
                chatMessageRepository.updateOutgoingMessagesStatusForChat(
                    chatId = chatId,
                    fromStatus = ChatMessage.Status.IS_SENT,
                    toStatus = ChatMessage.Status.NEW,
                )

            ContactSessionRole.Identity ->
                chatMessageRepository.updateOutgoingMessagesStatusForChatWithTypes(
                    chatId = chatId,
                    fromStatus = ChatMessage.Status.IS_SENT,
                    toStatus = ChatMessage.Status.NEW,
                    contentTypes = IDENTITY_SESSION_CONTENT_TYPES,
                )

            ContactSessionRole.MultiDevice ->
                chatMessageRepository.updateOutgoingMessagesStatusForChatExcludingTypes(
                    chatId = chatId,
                    fromStatus = ChatMessage.Status.IS_SENT,
                    toStatus = ChatMessage.Status.NEW,
                    contentTypes = IDENTITY_SESSION_CONTENT_TYPES,
                )
        }
    }

    private suspend fun tryNotifyNewMessageSent(message: ChatMessage) {
        val isVoIP = message.content is ChatMessage.Content.DataChannelOffer

        message.toEncodedMessage()
            .map { pushNotificationSession.encrypt(it) }
            .map { encrypted ->
                callbacks.onShouldNotifyNewMessageSent(
                    messageId = message.id,
                    accountId = pushNotificationSession.remoteAccount.accountId,
                    pushId = outgoingPushId,
                    encryptedMessage = encrypted,
                    isVoIP = isVoIP
                )
            }.logFailure("Failed to notify that new message was sent")
    }

    fun dispose() {
        cancel()
    }
}
