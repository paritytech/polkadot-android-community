package io.paritytech.polkadotapp.feature_chats_impl.domain.extension

import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId

internal fun claimGroupOf(messageId: ChatMessageId) = CoinageOperationGroupId("chat-claim:$messageId")

internal fun reclaimGroupOf(messageId: ChatMessageId) = CoinageOperationGroupId("chat-reclaim:$messageId")
