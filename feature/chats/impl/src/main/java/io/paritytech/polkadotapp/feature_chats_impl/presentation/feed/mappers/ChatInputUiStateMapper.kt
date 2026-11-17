package io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.mappers

import io.paritytech.polkadotapp.feature_chats_impl.domain.models.ChatUserInputState
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models.ChatInputUiState
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models.ChatRequestAnswerProgress
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models.ChatSendMessageInputState
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models.clearRelation

internal fun ChatUserInputState.toChatInputUiState(
    messageInputState: ChatSendMessageInputState,
    answerProgress: ChatRequestAnswerProgress
): ChatInputUiState {
    return when (this) {
        is ChatUserInputState.AcceptChatRequest -> ChatInputUiState.AcceptChatRequest(answerProgress)
        is ChatUserInputState.Nothing, ChatUserInputState.UserDeclinedChatRequest -> ChatInputUiState.Hidden
        is ChatUserInputState.PeerLeft -> ChatInputUiState.PeerLeft
        is ChatUserInputState.ChatRequestCancelled -> ChatInputUiState.ChatRequestCancelled
        is ChatUserInputState.ChatRemoved -> ChatInputUiState.Hidden
        is ChatUserInputState.UnblockUser -> ChatInputUiState.UnblockUser
        is ChatUserInputState.IdentitySigningConsentRequired -> ChatInputUiState.IdentitySigningConsent
        is ChatUserInputState.WaitChatRequestApproval -> ChatInputUiState.WaitChatRequestApproval
        is ChatUserInputState.SendChatRequest -> {
            ChatInputUiState.SendMessage(messageInputState.clearRelation(), isChatRequest = true)
        }

        is ChatUserInputState.SendMessage -> {
            ChatInputUiState.SendMessage(
                messageState = messageInputState,
                isChatRequest = false,
                showPayButton = paymentState is ChatUserInputState.SendMessage.Payment.Available,
                showAttachButton = attachmentsSupported
            )
        }
    }
}
