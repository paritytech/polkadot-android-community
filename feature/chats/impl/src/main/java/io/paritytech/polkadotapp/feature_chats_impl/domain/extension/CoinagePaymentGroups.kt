package io.paritytech.polkadotapp.feature_chats_impl.domain.extension

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.deriveKeypair
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId

/**
 * Derived from the message rather than stored, so a retry after process death rejoins the claims the previous
 * attempt registered instead of submitting a second set.
 */
internal fun claimGroupOf(messageId: ChatMessageId) = CoinageOperationGroupId("chat-claim:$messageId")

internal fun reclaimGroupOf(messageId: ChatMessageId) = CoinageOperationGroupId("chat-reclaim:$messageId")

internal fun CoinagePayment.coinAccountIds(): List<AccountId> =
    coinKeys.map { it.toDataByteArray().deriveKeypair().publicKey.intoAccountId() }
