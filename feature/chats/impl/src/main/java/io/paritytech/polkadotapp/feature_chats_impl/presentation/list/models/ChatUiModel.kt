package io.paritytech.polkadotapp.feature_chats_impl.presentation.list.models

import androidx.compose.runtime.Immutable
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.feature_chats_api.presentation.model.ChatPreviewUiModel
import io.paritytech.polkadotapp.feature_chats_impl.presentation.feed.models.ChatDisplayUiModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ChatListScreenUiState(
    val chainsHealth: ChainHealthIndicatorsModel,
    val chats: LoadingState<ChatListUiState>,
)

@Immutable
data class ChatListUiState(
    val chats: ImmutableList<ChatItem>,
    val pendingRequestsCount: Int,
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
