package io.paritytech.polkadotapp.feature_chats_api.presentation.model

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.feature_chats_api.domain.middleware.bot.CustomChatMessageRenderer
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageOrigin
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import javax.annotation.concurrent.Immutable
import kotlin.time.Duration

sealed interface ChatMessageUiModel {
    val id: ChatMessageId
    val timestamp: Timestamp
    val direction: Direction
    val status: Status
    val origin: ChatMessageOrigin

    data class Text(
        override val id: ChatMessageId,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: Status,
        override val origin: ChatMessageOrigin,
        val text: String,
        val replyPreview: ReplyPreview?,
        val reactions: List<Reaction>,
        val isEdited: Boolean
    ) : ChatMessageUiModel

    data class ContactAdded(
        override val id: ChatMessageId,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: Status,
        override val origin: ChatMessageOrigin,
    ) : ChatMessageUiModel

    data class CoinagePayment(
        override val id: ChatMessageId,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: ChatMessageUiModel.Status,
        override val origin: ChatMessageOrigin,
        val paymentStatus: Status,
        val amount: TokenAmountModel,
        val reactions: List<Reaction>
    ) : ChatMessageUiModel {
        val differingAmount: TokenAmountModel?
            get() = paymentStatus.settledAmountOrNull()?.takeIf { it.amount.compareTo(amount.amount) != 0 }

        sealed interface Status {
            data object Detecting : Status
            data class Detected(val detected: TokenAmountModel) : Status
            data class Transferred(val transferred: TokenAmountModel) : Status
            data object FailedDetection : Status
            data object FailedTransfer : Status
        }
    }

    data class Multimedia(
        override val id: ChatMessageId,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: Status,
        override val origin: ChatMessageOrigin,
        val uri: Uri?,
        val text: String?,
        val type: MultimediaType,
        val blurHash: String?,
        val uploadState: UploadState?,
        val reactions: List<Reaction>,
        val isEdited: Boolean
    ) : ChatMessageUiModel {
        sealed interface UploadState {
            data class Uploading(val progressPercent: Int) : UploadState
            data class Failed(val errorMessage: String) : UploadState
        }

        sealed interface MultimediaType {
            data class Image(val height: Int, val width: Int) : MultimediaType
            data class Video(val duration: Duration) : MultimediaType
        }
    }

    data class File(
        override val id: ChatMessageId,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: Status,
        override val origin: ChatMessageOrigin,
        val uri: Uri,
        val fileName: String,
        val size: InformationSize,
        val thumbnailUri: Uri?,
        val text: String?,
    ) : ChatMessageUiModel

    data class Unsupported(
        override val id: ChatMessageId,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: Status,
        override val origin: ChatMessageOrigin,
    ) : ChatMessageUiModel

    data class ChatRequest(
        override val id: ChatMessageId,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: Status,
        override val origin: ChatMessageOrigin,
        val welcomeText: String?,
        val reactions: List<Reaction>,
    ) : ChatMessageUiModel

    data class ChatAccepted(
        override val id: ChatMessageId,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: Status,
        override val origin: ChatMessageOrigin,
        val peerUsername: String
    ) : ChatMessageUiModel

    data class Call(
        override val id: ChatMessageId,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: Status,
        override val origin: ChatMessageOrigin,
        val purpose: Purpose,
        val state: State
    ) : ChatMessageUiModel {
        enum class Purpose {
            AUDIO_CALL, VIDEO_CALL
        }

        sealed interface State {
            data object Ringing : State
            data object Ongoing : State
            data class Ended(val duration: Duration) : State
            data object Missed : State
            data class Canceled(val duration: Duration) : State
            data class Declined(val duration: Duration) : State
        }
    }

    data class Custom<T>(
        override val id: String,
        override val timestamp: Timestamp,
        override val direction: Direction,
        override val status: Status,
        override val origin: ChatMessageOrigin,
        val renderer: CustomChatMessageRenderer<T>,
        val content: Result<T>
    ) : ChatMessageUiModel

    enum class Status { PENDING, SENT, READ }
    enum class Direction { INCOMING, OUTGOING }

    data class Reaction(val count: Int, val emoji: String, val reactedByUser: Boolean)
}

@Immutable
data class ReplyPreview(
    val messageId: ChatMessageId,
    val title: String,
    val text: String?
)

@Immutable
data class HighlightedMessage(
    val messageId: ChatMessageId,
    val scrollIndex: Int
)

@Immutable
data class FirstNewMessageInfo(
    val index: Int,
    val messageId: ChatMessageId
)

@Immutable
data class MessageRevisionUiModel(
    val text: String,
    val timestamp: Long
)

sealed class MessagePopUpUiState {
    data class ReactionsDetails(
        val messageId: ChatMessageId,
        val reactionsByEmoji: ImmutableList<EmojiReactionGroup>,
        val totalReactionsCount: Int
    ) : MessagePopUpUiState()

    data class ActionMenu(
        val message: ChatMessageUiModel,
        val userReactedEmojis: ImmutableSet<String>,
        val canLeaveReactions: Boolean,
        val allowedMenuActions: List<AllowedMessageMenuAction>
    ) : MessagePopUpUiState()
}

private fun ChatMessageUiModel.CoinagePayment.Status.settledAmountOrNull(): TokenAmountModel? = when (this) {
    is ChatMessageUiModel.CoinagePayment.Status.Detected -> detected
    is ChatMessageUiModel.CoinagePayment.Status.Transferred -> transferred
    else -> null
}

fun ChatMessageUiModel.isUnread(): Boolean {
    return direction == ChatMessageUiModel.Direction.INCOMING && status != ChatMessageUiModel.Status.READ
}

fun ChatMessageUiModel.isOutgoing(): Boolean {
    return direction == ChatMessageUiModel.Direction.OUTGOING
}

fun ChatMessageUiModel.isIncoming(): Boolean {
    return direction == ChatMessageUiModel.Direction.INCOMING
}

data class MessageLayoutInfo(
    val offset: Offset,
    val size: IntSize
)

sealed interface MessageAction {
    data class LongPress(val message: ChatMessageUiModel) : MessageAction
    data class Press(val message: ChatMessageUiModel) : MessageAction
    data class Reply(val message: ChatMessageUiModel) : MessageAction
    data class Copy(val text: String) : MessageAction
    data class Edit(val message: ChatMessageUiModel, val text: String) : MessageAction
    data class Reaction(val message: ChatMessageUiModel, val emoji: String) : MessageAction
    data class ShowReactionDetails(val message: ChatMessageUiModel) : MessageAction
    data class ReplyPreviewTap(val messageId: ChatMessageId) : MessageAction
    data class ViewEditHistory(val message: ChatMessageUiModel) : MessageAction
    data object DismissActionMenu : MessageAction
}

sealed interface AllowedMessageMenuAction {
    data object Reply : AllowedMessageMenuAction
    data class Copy(val text: String) : AllowedMessageMenuAction
    data class Edit(val text: String) : AllowedMessageMenuAction
    data object ViewEditHistory : AllowedMessageMenuAction
}
