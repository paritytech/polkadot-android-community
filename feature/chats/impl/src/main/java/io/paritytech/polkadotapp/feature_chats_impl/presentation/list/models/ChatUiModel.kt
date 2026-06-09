package io.paritytech.polkadotapp.feature_chats_impl.presentation.list.models

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatPreviewUiModel
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models.ChatDisplayUiModel

data class ChatListUiState(
    val chats: List<ChatItem>,
    val pendingRequestsCount: Int
) {
    val hasNewRequests: Boolean = pendingRequestsCount > 0

    data class ChatItem(
        val chatId: ChatId,
        val display: ChatDisplayUiModel,
        val badge: Badge,
        val preview: ChatPreviewUiModel?,
        val isMuted: Boolean,
        val hasReaction: Boolean,
    ) {
        val uniqueKey: String = chatId.value.value.toHexString()
    }

    sealed interface Badge {
        data class Unread(
            val count: Int
        ) : Badge

        object None : Badge
    }
}
