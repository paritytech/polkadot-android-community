package io.paritytech.polkadotapp.feature_chats_api.domain.interactors

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface ChatStateSyncUseCase {
    class SettledPayment(
        val contactAccountId: AccountId,
        val messageId: ChatMessageId,
        val outcome: Outcome,
    ) {
        sealed interface Outcome {
            class Claimed(val partial: Balance?) : Outcome

            data object Failed : Outcome
        }
    }

    suspend fun getChatState(changedAfter: Instant?): ChatStateForSync

    suspend fun getSettledPayments(updatedAfter: Instant?): List<SettledPayment>

    fun observeChatStateChanges(): Flow<Unit>
}
