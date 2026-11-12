package io.paritytech.polkadotapp.feature_chats_api.domain.interactors

import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatStateSyncUseCase.SettledPayment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync

interface ApplySyncedChatStateUseCase {
    suspend fun applyBlockStates(states: List<ChatStateForSync.BlockState>)

    suspend fun applyIncomingRequests(requests: List<ChatStateForSync.IncomingRequest>)

    suspend fun applyIncomingRequestUpdates(updates: List<ChatStateForSync.IncomingRequestUpdate>)

    suspend fun applyPendingOutgoingRequests(requests: List<ChatStateForSync.OutgoingRequest>): Result<Unit>

    suspend fun applyOutgoingRequestUpdates(updates: List<ChatStateForSync.OutgoingRequestUpdate>, removedAt: Long)

    suspend fun applyContactDevices(changes: List<ChatStateForSync.ContactDeviceChange>)

    suspend fun applySettledPayments(payments: List<SettledPayment>): List<SettledPayment>
}
