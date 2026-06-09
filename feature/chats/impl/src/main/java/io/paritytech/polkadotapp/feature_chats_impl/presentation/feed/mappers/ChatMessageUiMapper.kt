package io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.mappers

import io.paritytech.polkadotapp.common.utils.mapToSet
import io.paritytech.polkadotapp.common.utils.nullIfEmpty
import io.paritytech.polkadotapp.feature_calls_api.domain.models.ActiveCallState
import io.paritytech.polkadotapp.feature_chats_api.domain.middleware.bot.CustomChatMessageRenderer
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Attachment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageDirection
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageReaction
import io.paritytech.polkadotapp.feature_chats_api.domain.model.MessageRevision
import io.paritytech.polkadotapp.feature_chats_api.domain.model.direction
import io.paritytech.polkadotapp.feature_chats_api.domain.model.isUser
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatMessageUiModel
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.EmojiReactionGroup
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.MessagePopUpUiState
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ReactionDetail
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ReplyPreview
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.ChatUserInputState
import io.paritytech.polkadotapp.feature_chats_impl.domain.originDisplay.MessageOriginDisplayResolver
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.ChatMenuActionsProvider
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models.InputMessageRelation
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models.MessagePopUpSelection
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models.toUi
import io.paritytech.polkadotapp.feature_chats_impl.presentation.util.CallResolutionContext
import io.paritytech.polkadotapp.feature_chats_impl.presentation.util.buildCallResolutionContext
import io.paritytech.polkadotapp.feature_chats_impl.presentation.util.resolveCallState
import io.paritytech.polkadotapp.feature_chats_impl.presentation.util.toCallPurposeUi
import io.paritytech.polkadotapp.feature_chats_impl.utils.ChatMessageMappingHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import javax.inject.Inject

class ChatMessageUiMapper @Inject constructor(
    private val helper: ChatMessageMappingHelper,
    private val chatMenuActionsProvider: ChatMenuActionsProvider,
) {
    suspend fun map(
        messages: List<ChatMessage>,
        originDisplayResolver: MessageOriginDisplayResolver,
        reactions: Map<ChatMessageId, List<ChatMessageReaction>>,
        revisions: Map<ChatMessageId, MessageRevision>,
        customMessageRenderers: Map<String, CustomChatMessageRenderer<*>> = emptyMap(),
        activeCall: ActiveCallState? = null
    ): ImmutableList<ChatMessageUiModel> {
        val messagesById: Map<String, ChatMessage> = messages.associateBy { it.id }
        val callContext = buildCallResolutionContext(messages, activeCall)

        return messages.mapNotNull { message ->
            val messageReactions = reactions[message.id].orEmpty()
                .groupBy { it.content.emoji }
                .map { (emoji, reactions) ->
                    ChatMessageUiModel.Reaction(
                        count = reactions.size,
                        emoji = emoji,
                        reactedByUser = reactions.any { it.origin.isUser() }
                    )
                }

            val revision = revisions[message.id]
            val latestContent = revision?.content ?: message.content
            val isEdited = revision != null

            message.toUi(
                latestContent = latestContent,
                isEdited = isEdited,
                messagesById = messagesById,
                revisions = revisions,
                originDisplayResolver = originDisplayResolver,
                messageReactions = messageReactions,
                customMessageRenderers = customMessageRenderers,
                callContext = callContext
            )
        }.sortedByDescending { it.timestamp }
            .toImmutableList()
    }

    fun constructPopUpState(
        selection: MessagePopUpSelection,
        reactions: Map<ChatMessageId, List<ChatMessageReaction>>,
        originDisplayResolver: MessageOriginDisplayResolver,
        messages: List<ChatMessageUiModel>,
        userInputState: ChatUserInputState,
    ): MessagePopUpUiState? {
        return when (selection.type) {
            MessagePopUpSelection.Type.ACTIONS -> constructActionMenuPopUpState(selection.messageId, reactions, messages, userInputState)
            MessagePopUpSelection.Type.REACTION_DETAILS -> constructReactionDetailsPopUpState(selection.messageId, reactions, originDisplayResolver)
        }
    }

    fun createReplyRelationFor(
        message: ChatMessageUiModel,
        originDisplayResolver: MessageOriginDisplayResolver,
    ): InputMessageRelation.Reply? {
        return when (message) {
            is ChatMessageUiModel.Text -> {
                InputMessageRelation.Reply(
                    messageId = message.id,
                    title = originDisplayResolver.displayOf(message.origin).name,
                    text = message.text
                )
            }

            is ChatMessageUiModel.ChatRequest -> {
                InputMessageRelation.Reply(
                    messageId = message.id,
                    title = originDisplayResolver.displayOf(message.origin).name,
                    text = message.welcomeText ?: return null
                )
            }

            is ChatMessageUiModel.Multimedia -> {
                InputMessageRelation.Reply(
                    messageId = message.id,
                    title = originDisplayResolver.displayOf(message.origin).name,
                    text = message.text
                )
            }

            is ChatMessageUiModel.File -> {
                InputMessageRelation.Reply(
                    messageId = message.id,
                    title = originDisplayResolver.displayOf(message.origin).name,
                    text = message.text
                )
            }

            else -> null
        }
    }

    private suspend fun ChatMessage.toUi(
        latestContent: ChatMessage.Content,
        isEdited: Boolean,
        messagesById: Map<String, ChatMessage>,
        revisions: Map<ChatMessageId, MessageRevision>,
        originDisplayResolver: MessageOriginDisplayResolver,
        messageReactions: List<ChatMessageUiModel.Reaction>,
        customMessageRenderers: Map<String, CustomChatMessageRenderer<*>>,
        callContext: CallResolutionContext
    ): ChatMessageUiModel? {
        val direction = direction.toUi()
        val status = status.toUi()

        return when (latestContent) {
            is ChatMessage.Content.Text -> mapText(
                message = this,
                content = latestContent,
                isEdited = isEdited,
                direction = direction,
                status = status,
                messagesById = messagesById,
                revisions = revisions,
                originDisplayResolver = originDisplayResolver,
                messageReactions = messageReactions
            )

            is ChatMessage.Content.ContactAdded -> ChatMessageUiModel.ContactAdded(
                id = id,
                timestamp = timestamp,
                direction = direction,
                status = status,
                origin = origin,
            )

            is ChatMessage.Content.RichText -> mapRichText(
                message = this,
                content = latestContent,
                direction = direction,
                status = status,
                messagesById = messagesById,
                revisions = revisions,
                originDisplayResolver = originDisplayResolver,
                messageReactions = messageReactions,
                isEdited = isEdited
            )

            is ChatMessage.Content.CoinagePayment -> {
                val tokenAmount = helper.extractTokenAmount(latestContent)

                ChatMessageUiModel.CoinagePayment(
                    id = id,
                    timestamp = timestamp,
                    direction = direction,
                    status = status,
                    amount = tokenAmount,
                    paymentStatus = helper.mapPaymentStatus(latestContent.status),
                    origin = origin,
                    reactions = messageReactions
                )
            }

            is ChatMessage.Content.Unsupported -> ChatMessageUiModel.Unsupported(
                id = id,
                timestamp = timestamp,
                direction = direction,
                status = status,
                origin = origin,
            )

            is ChatMessage.Content.ChatRequest -> mapChatRequest(
                message = this,
                content = latestContent,
                direction = direction,
                status = status,
                messageReactions = messageReactions
            )

            is ChatMessage.Content.ChatAccepted,
            is ChatMessage.Content.DeviceChatAccepted -> ChatMessageUiModel.ChatAccepted(
                id = id,
                timestamp = timestamp,
                direction = direction,
                status = status,
                origin = origin,
                peerUsername = originDisplayResolver.displayOf(origin).name
            )

            is ChatMessage.Content.DataChannelOffer -> ChatMessageUiModel.Call(
                id = id,
                timestamp = timestamp,
                direction = direction,
                status = status,
                origin = origin,
                purpose = latestContent.purpose.toCallPurposeUi(),
                state = resolveCallState(offer = this, callContext = callContext)
            )

            is ChatMessage.Content.LeftChat,
            is ChatMessage.Content.Token,
            is ChatMessage.Content.Reacted,
            is ChatMessage.Content.ReactionRemoved,
            is ChatMessage.Content.DataChannelAnswer,
            is ChatMessage.Content.DataChannelIceCandidate,
            is ChatMessage.Content.DataChannelClosed,
            is ChatMessage.Content.DeviceAdded,
            is ChatMessage.Content.DeviceRemoved,
            is ChatMessage.Content.Edited -> null

            is ChatMessage.Content.Custom<*> -> {
                @Suppress("UNCHECKED_CAST")
                val renderer = customMessageRenderers[latestContent.rendererId] as? CustomChatMessageRenderer<Any?>

                if (renderer != null) {
                    ChatMessageUiModel.Custom(
                        id = id,
                        timestamp = timestamp,
                        direction = direction,
                        status = status,
                        renderer = renderer,
                        origin = origin,
                        content = latestContent.content
                    )
                } else {
                    // Fallback to Unsupported if renderer not found
                    ChatMessageUiModel.Unsupported(
                        id = id,
                        timestamp = timestamp,
                        direction = direction,
                        status = status,
                        origin = origin,
                    )
                }
            }
        }
    }

    private fun constructActionMenuPopUpState(
        messageId: ChatMessageId,
        reactions: Map<ChatMessageId, List<ChatMessageReaction>>,
        messages: List<ChatMessageUiModel>,
        userInputState: ChatUserInputState,
    ): MessagePopUpUiState.ActionMenu? {
        val messageReactions = reactions[messageId].orEmpty()
        val userReactedEmojis = messageReactions
            .filter { it.origin.isUser() }
            .mapToSet { it.content.emoji }
            .toImmutableSet()

        val message = messages.find { it.id == messageId } ?: return null

        return MessagePopUpUiState.ActionMenu(
            message = message,
            userReactedEmojis = userReactedEmojis,
            canLeaveReactions = chatMenuActionsProvider.canLeaveMenuReactions(userInputState),
            allowedMenuActions = chatMenuActionsProvider.getMessageActions(message, userInputState),
        )
    }
}

private fun ChatMessage.Status.toUi(): ChatMessageUiModel.Status {
    return when (this) {
        ChatMessage.Status.PROCESSING,
        ChatMessage.Status.NEW -> ChatMessageUiModel.Status.PENDING

        ChatMessage.Status.IS_SENT -> ChatMessageUiModel.Status.SENT
        ChatMessage.Status.IS_READ -> ChatMessageUiModel.Status.READ
    }
}

private fun ChatMessageDirection.toUi(): ChatMessageUiModel.Direction {
    return when (this) {
        ChatMessageDirection.OUTGOING -> ChatMessageUiModel.Direction.OUTGOING
        ChatMessageDirection.INCOMING -> ChatMessageUiModel.Direction.INCOMING
    }
}

private fun mapText(
    message: ChatMessage,
    content: ChatMessage.Content.Text,
    isEdited: Boolean,
    direction: ChatMessageUiModel.Direction,
    status: ChatMessageUiModel.Status,
    messagesById: Map<String, ChatMessage>,
    revisions: Map<ChatMessageId, MessageRevision>,
    originDisplayResolver: MessageOriginDisplayResolver,
    messageReactions: List<ChatMessageUiModel.Reaction>
): ChatMessageUiModel.Text {
    val replyPreview = buildReplyPreview(
        message = message,
        allMessages = messagesById,
        revisions = revisions,
        originDisplayResolver = originDisplayResolver
    )

    return ChatMessageUiModel.Text(
        id = message.id,
        timestamp = message.timestamp,
        direction = direction,
        status = status,
        text = content.text,
        replyPreview = replyPreview,
        reactions = messageReactions,
        origin = message.origin,
        isEdited = isEdited
    )
}

private fun mapChatRequest(
    message: ChatMessage,
    content: ChatMessage.Content.ChatRequest,
    direction: ChatMessageUiModel.Direction,
    status: ChatMessageUiModel.Status,
    messageReactions: List<ChatMessageUiModel.Reaction>
): ChatMessageUiModel.ChatRequest {
    return ChatMessageUiModel.ChatRequest(
        id = message.id,
        timestamp = message.timestamp,
        direction = direction,
        status = status,
        origin = message.origin,
        welcomeText = content.welcome?.text,
        reactions = messageReactions
    )
}

private fun mapRichText(
    message: ChatMessage,
    content: ChatMessage.Content.RichText,
    direction: ChatMessageUiModel.Direction,
    status: ChatMessageUiModel.Status,
    messagesById: Map<String, ChatMessage>,
    revisions: Map<ChatMessageId, MessageRevision>,
    originDisplayResolver: MessageOriginDisplayResolver,
    isEdited: Boolean,
    messageReactions: List<ChatMessageUiModel.Reaction>
): ChatMessageUiModel? {
    val replyPreview = buildReplyPreview(
        message = message,
        allMessages = messagesById,
        revisions = revisions,
        originDisplayResolver = originDisplayResolver
    )

    return mapAttachmentToUi(
        message = message,
        text = content.text,
        attachment = content.attachments.firstOrNull(),
        direction = direction,
        status = status,
        messageReactions = messageReactions,
        isEdited = isEdited,
        replyPreview = replyPreview
    )
}

private fun mapAttachmentToUi(
    message: ChatMessage,
    text: String?,
    attachment: Attachment?,
    direction: ChatMessageUiModel.Direction,
    status: ChatMessageUiModel.Status,
    messageReactions: List<ChatMessageUiModel.Reaction>,
    isEdited: Boolean,
    replyPreview: ReplyPreview?,
): ChatMessageUiModel? {
    // TODO: implement reactions for messages with attachments
    if (attachment == null) {
        val messageText = text ?: return null

        return ChatMessageUiModel.Text(
            id = message.id,
            timestamp = message.timestamp,
            direction = direction,
            status = status,
            text = messageText,
            replyPreview = replyPreview,
            reactions = messageReactions,
            origin = message.origin,
            isEdited = isEdited
        )
    }

    return when (val meta = attachment.meta) {
        is Attachment.Meta.Image -> ChatMessageUiModel.Multimedia(
            id = message.id,
            timestamp = message.timestamp,
            direction = direction,
            status = status,
            uri = attachment.uri,
            text = text,
            type = ChatMessageUiModel.Multimedia.MultimediaType.Image(height = meta.height, width = meta.width),
            blurHash = meta.blurHash,
            uploadState = null,
            origin = message.origin,
            reactions = messageReactions,
            isEdited = isEdited
        )

        is Attachment.Meta.Video -> ChatMessageUiModel.Multimedia(
            id = message.id,
            timestamp = message.timestamp,
            direction = direction,
            status = status,
            uri = attachment.uri,
            text = text,
            type = ChatMessageUiModel.Multimedia.MultimediaType.Video(kotlin.time.Duration.ZERO),
            blurHash = meta.blurHash,
            uploadState = null,
            origin = message.origin,
            reactions = messageReactions,
            isEdited = isEdited
        )

        is Attachment.Meta.General -> ChatMessageUiModel.File(
            id = message.id,
            timestamp = message.timestamp,
            direction = direction,
            status = status,
            fileName = meta.fileName,
            size = meta.size,
            thumbnailUri = null,
            text = text,
            origin = message.origin,
            uri = attachment.uri ?: return null,
        )
    }
}

private fun constructReactionDetailsPopUpState(
    messageId: ChatMessageId,
    reactions: Map<ChatMessageId, List<ChatMessageReaction>>,
    originDisplayResolver: MessageOriginDisplayResolver
): MessagePopUpUiState.ReactionsDetails {
    val messageReactions = reactions[messageId].orEmpty()

    val groupedReactions = messageReactions
        .groupBy { it.content.emoji }
        .map { (emoji, reactions) ->
            constructEmojiReactionGroup(emoji, reactions, originDisplayResolver)
        }
        .sortedByDescending { it.reactions.size }
        .toImmutableList()

    return MessagePopUpUiState.ReactionsDetails(
        messageId = messageId,
        reactionsByEmoji = groupedReactions,
        totalReactionsCount = messageReactions.size
    )
}

private fun constructEmojiReactionGroup(
    emoji: String,
    reactions: List<ChatMessageReaction>,
    originDisplayResolver: MessageOriginDisplayResolver
): EmojiReactionGroup {
    return EmojiReactionGroup(
        emoji = emoji,
        reactions = reactions
            .sortedByDescending { it.timestamp }
            .map { reaction ->
                val originDisplay = originDisplayResolver.displayOf(reaction.origin)

                ReactionDetail(
                    emoji = emoji,
                    authorName = originDisplay.name,
                    timestamp = reaction.timestamp,
                    isUser = reaction.origin.isUser(),
                    avatarModel = originDisplay.avatar.toUi()
                )
            }
            .toImmutableList()
    )
}

private fun buildReplyPreview(
    message: ChatMessage,
    allMessages: Map<String, ChatMessage>,
    revisions: Map<ChatMessageId, MessageRevision>,
    originDisplayResolver: MessageOriginDisplayResolver,
): ReplyPreview? {
    val repliedId = message.replyToMessageId ?: return null
    val repliedMessage = allMessages[repliedId] ?: return null

    val revision = revisions[repliedId]
    val content = revision?.content ?: repliedMessage.content

    val text = when (content) {
        is ChatMessage.Content.Text -> content.text
        is ChatMessage.Content.ChatRequest -> content.welcome?.text ?: return null
        is ChatMessage.Content.RichText -> content.text.nullIfEmpty()

        is ChatMessage.Content.ChatAccepted,
        is ChatMessage.Content.DeviceChatAccepted,
        ChatMessage.Content.ContactAdded,
        is ChatMessage.Content.Custom<*>,
        is ChatMessage.Content.Edited,
        ChatMessage.Content.LeftChat,
        is ChatMessage.Content.CoinagePayment,
        is ChatMessage.Content.Reacted,
        is ChatMessage.Content.ReactionRemoved,
        is ChatMessage.Content.Token,
        is ChatMessage.Content.Unsupported,
        is ChatMessage.Content.DataChannelAnswer,
        is ChatMessage.Content.DataChannelIceCandidate,
        is ChatMessage.Content.DataChannelOffer,
        is ChatMessage.Content.DataChannelClosed,
        is ChatMessage.Content.DeviceAdded,
        is ChatMessage.Content.DeviceRemoved -> return null
    }

    val title = originDisplayResolver.displayOf(repliedMessage.origin).name

    return ReplyPreview(
        messageId = repliedMessage.id,
        title = title,
        text = text
    )
}
