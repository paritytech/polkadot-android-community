package io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models

import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId

sealed class ChatInputUiState {
    data class SendMessage(
        val messageState: ChatSendMessageInputState,
        val isChatRequest: Boolean,
        val showPayButton: Boolean = false,
        val showAttachButton: Boolean = false,
    ) : ChatInputUiState()

    data class AcceptChatRequest(
        val answerProgress: ChatRequestAnswerProgress
    ) : ChatInputUiState()

    object WaitChatRequestApproval : ChatInputUiState()

    object PeerLeft : ChatInputUiState()

    object UnblockUser : ChatInputUiState()

    object Hidden : ChatInputUiState()
}

enum class ChatRequestAnswerProgress {
    None, Accepting, Declining
}

fun ChatInputUiState.sendableMessageState(): ChatSendMessageInputState? {
    return (this as? ChatInputUiState.SendMessage)?.messageState
}

data class ChatSendMessageInputState(
    val inputMessage: String = "",
    val relation: InputMessageRelation = InputMessageRelation.None
)

fun ChatSendMessageInputState.sendableMessageText(): String? {
    return inputMessage.trim().takeIf { it.isNotEmpty() }
}

fun ChatSendMessageInputState.clearRelation(): ChatSendMessageInputState {
    return copy(relation = InputMessageRelation.None)
}

sealed class InputMessageRelation {
    data object None : InputMessageRelation()

    data class Reply(
        val messageId: ChatMessageId,
        val title: String,
        val text: String?
    ) : InputMessageRelation()

    data class Edit(
        val messageId: ChatMessageId,
        val originalText: String
    ) : InputMessageRelation()
}

fun InputMessageRelation.isNotNone(): Boolean {
    return this !is InputMessageRelation.None
}
