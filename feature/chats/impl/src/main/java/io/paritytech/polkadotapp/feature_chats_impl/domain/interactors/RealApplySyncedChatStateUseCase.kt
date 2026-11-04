package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplySyncedChatStateUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.RemoveContactUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.identitySigning.IdentitySigningConsentUseCase
import javax.inject.Inject

class RealApplySyncedChatStateUseCase @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatRequestRepository: ChatRequestRepository,
    private val addContactUseCase: RealAddContactUseCase,
    private val removeContactUseCase: RemoveContactUseCase,
    private val identitySigningConsent: IdentitySigningConsentUseCase,
) : ApplySyncedChatStateUseCase {
    override suspend fun applyBlockStates(states: List<ChatStateForSync.BlockState>) {
        states.forEach { state ->
            val contact = contactsRepository.getContact(state.accountId) ?: return@forEach
            if (contact.isBlocked == state.isBlocked) return@forEach

            contactsRepository.setBlockedFromSync(state.accountId, state.isBlocked)
            if (state.isBlocked) identitySigningConsent.revoke(state.accountId)
        }
    }

    override suspend fun applyIncomingRequests(requests: List<ChatStateForSync.IncomingRequest>) {
        requests.forEach { request ->
            if (isLinkedToContact(request.id, request.peer)) return@forEach

            chatRequestRepository.saveFromSync(
                ChatRequest(
                    welcomeMessageId = request.id,
                    peer = request.peer,
                    timestamp = request.timestamp,
                    direction = ChatRequest.Direction.INCOMING,
                    status = incomingStatus(request.isAccepted),
                    delivery = ChatRequest.Delivery.DeliveredUntracked,
                )
            )
        }
    }

    override suspend fun applyIncomingRequestUpdates(updates: List<ChatStateForSync.IncomingRequestUpdate>) {
        updates.forEach { update ->
            val peer = chatRequestRepository.getById(update.id)?.peer ?: return@forEach
            if (isLinkedToContact(update.id, peer)) return@forEach

            chatRequestRepository.updateStatusFromSync(update.id, incomingStatus(update.isAccepted))
        }
    }

    override suspend fun applyPendingOutgoingRequests(requests: List<ChatStateForSync.OutgoingRequest>): Result<Unit> {
        return addContactUseCase.addContactsWithPendingRequests(requests)
    }

    override suspend fun applyOutgoingRequestUpdates(updates: List<ChatStateForSync.OutgoingRequestUpdate>, removedAt: Long) {
        updates.filter { it.isCancelled }.forEach { update ->
            val peer = chatRequestRepository.getById(update.id)?.peer ?: return@forEach

            if (isLinkedToContact(update.id, peer)) removeContactUseCase.recordRemoteContactRemoval(peer, removedAt)
        }
    }

    private suspend fun isLinkedToContact(requestId: ChatRequestId, peer: AccountId): Boolean {
        return contactsRepository.getContact(peer)?.pendingChatRequestId == requestId
    }

    private fun incomingStatus(isAccepted: Boolean): ChatRequest.Status {
        return if (isAccepted) ChatRequest.Status.ACCEPTED else ChatRequest.Status.DECLINED
    }
}
