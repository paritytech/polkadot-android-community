package io.paritytech.polkadotapp.feature_chats_api.domain.interactors

import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface ChatStateSyncUseCase {
    suspend fun getChatState(changedAfter: Instant?): ChatStateForSync

    fun observeChatStateChanges(): Flow<Unit>
}
