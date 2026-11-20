package io.paritytech.polkadotapp.feature_chats_impl.domain.extension

import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId

/**
 * Derived from the message rather than stored, so a retry after process death rejoins the claims the previous
 * attempt registered instead of submitting a second set.
 */
internal fun claimGroupOf(messageId: ChatMessageId) = CoinageOperationGroupId("chat-claim:$messageId")

internal fun reclaimGroupOf(messageId: ChatMessageId) = CoinageOperationGroupId("chat-reclaim:$messageId")
