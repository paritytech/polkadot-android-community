package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ApplySyncedChatStateUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatStateSyncUseCase.SettledIncomingPayment
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.RemoveContactUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageOrigin
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.AlwaysFailCustomContentDecoder
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageProcessingRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactDevicesRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.extension.CoinagePaymentProcessingExtension
import io.paritytech.polkadotapp.feature_chats_impl.domain.identitySigning.IdentitySigningConsentUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment.Status as PaymentStatus

class RealApplySyncedChatStateUseCase @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatRequestRepository: ChatRequestRepository,
    private val addContactUseCase: RealAddContactUseCase,
    private val removeContactUseCase: RemoveContactUseCase,
    private val identitySigningConsent: IdentitySigningConsentUseCase,
    private val contactDevicesRepository: ContactDevicesRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatMessageProcessingRepository: ChatMessageProcessingRepository,
) : ApplySyncedChatStateUseCase {
    private val customContentDecoder = AlwaysFailCustomContentDecoder()

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

    override suspend fun applyContactDevices(changes: List<ChatStateForSync.ContactDeviceChange>) {
        changes.groupBy { it.device.contactAccountId }.forEach { (contactAccountId, contactChanges) ->
            if (contactsRepository.getContact(contactAccountId) == null) return@forEach

            contactChanges.forEach { contactDevicesRepository.applySyncedDevice(it) }
        }
    }

    override suspend fun applySettledIncomingPayments(payments: List<SettledIncomingPayment>): List<SettledIncomingPayment> {
        return payments.filterNot { applySettledIncomingPayment(it) }
    }

    private suspend fun applySettledIncomingPayment(payment: SettledIncomingPayment): Boolean {
        val message = chatMessageRepository.subscribeMessageById(payment.messageId, customContentDecoder).first() ?: return false
        val content = message.content as? ChatMessage.Content.CoinagePayment ?: return true
        if (message.origin is ChatMessageOrigin.User || content.status.isSettled()) return true

        contactsRepository.withTransaction {
            val status = payment.outcome.toPaymentStatus(totalValue = content.totalValue)
            chatMessageRepository.updateMessageContent(message.id, content.copy(status = status), customContentDecoder)
            chatMessageProcessingRepository.markMessageProcessedByExtension(
                extensionId = CoinagePaymentProcessingExtension.ID,
                messageId = message.id,
                chatId = message.chatId,
            )
        }
        return true
    }

    private suspend fun isLinkedToContact(requestId: ChatRequestId, peer: AccountId): Boolean {
        return contactsRepository.getContact(peer)?.pendingChatRequestId == requestId
    }

    private fun incomingStatus(isAccepted: Boolean): ChatRequest.Status {
        return if (isAccepted) ChatRequest.Status.ACCEPTED else ChatRequest.Status.DECLINED
    }

    private fun PaymentStatus.isSettled(): Boolean = when (this) {
        is PaymentStatus.Transferred, PaymentStatus.FailedDetection, PaymentStatus.FailedTransfer -> true
        PaymentStatus.Detecting, is PaymentStatus.Detected, is PaymentStatus.PartiallyClaimed -> false
    }

    private fun SettledIncomingPayment.Outcome.toPaymentStatus(totalValue: Balance): PaymentStatus = when (this) {
        is SettledIncomingPayment.Outcome.Claimed -> PaymentStatus.Transferred(partial ?: totalValue)
        SettledIncomingPayment.Outcome.Failed -> PaymentStatus.FailedTransfer
    }
}
