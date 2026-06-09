package io.paritytech.polkadotapp.feature_chats_impl.presentation.list.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.paritytech.polkadotapp.common.utils.rememberCurrentTimeMillisWithDelay
import io.paritytech.polkadotapp.design.components.empty.EmptyScreenState
import io.paritytech.polkadotapp.design.components.navigationbar.LocalAppNavigationBarInsets
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_chats_impl.presentation.list.models.ChatListUiState
import kotlin.time.Duration.Companion.minutes
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
internal fun ChatListContent(
    state: ChatListUiState,
    onChatClick: (ChatListUiState.ChatItem) -> Unit,
    onNewRequestsClick: () -> Unit
) {
    val hasContent = state.chats.isNotEmpty() || state.hasNewRequests

    if (!hasContent) {
        EmptyScreenState(
            title = stringResource(RCommon.string.chats_empty_state_title),
            message = stringResource(RCommon.string.chats_empty_state_message)
        )
    } else {
        val currentTimestamp by rememberCurrentTimeMillisWithDelay(1.minutes)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = WindowInsets(
                bottom = PolkadotTheme.spacings.medium
            ).add(LocalAppNavigationBarInsets.current).asPaddingValues()
        ) {
            if (state.hasNewRequests) {
                item(key = "new_requests") {
                    NewRequestsItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PolkadotTheme.spacings.mediumIncreased)
                            .padding(bottom = PolkadotTheme.spacings.mediumIncreased)
                            .animateItem(),
                        count = state.pendingRequestsCount,
                        onClick = onNewRequestsClick
                    )
                }
            }

            items(
                count = state.chats.size,
                key = { i -> state.chats[i].uniqueKey },
            ) { i ->
                val chat = state.chats[i]

                Column(modifier = Modifier.animateItem()) {
                    PolkadotChatListItem(
                        modifier = Modifier.fillMaxWidth(),
                        chat = chat,
                        currentTimestamp = currentTimestamp,
                        onClick = { onChatClick(chat) },
                    )

                    if (i < state.chats.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = ChatListAvatarSize + PolkadotTheme.spacings.extraMedium * 2,
                                end = PolkadotTheme.spacings.extraMedium
                            )
                        )
                    }
                }
            }
        }
    }
}
