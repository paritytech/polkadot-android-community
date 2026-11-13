package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatStateSyncUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatStateSyncUseCase.SettledPayment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatVariant
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_api.domain.model.isIncoming
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactDevicesRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestService
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestPayloadLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Instant
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment.Status as PaymentStatus

class RealChatStateSyncUseCase @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatRequestRepository: ChatRequestRepository,
    private val payloadLoader: ChatRequestPayloadLoader,
    private val outgoingChatRequestService: OutgoingChatRequestService,
    private val contactDevicesRepository: ContactDevicesRepository,
    private val chatMessageRepository: ChatMessageRepository,
) : ChatStateSyncUseCase {
    override suspend fun getChatState(changedAfter: Instant?): ChatStateForSync {
        val requests = if (changedAfter == null) chatRequestRepository.getAll() else chatRequestRepository.getStatusChangedAfter(changedAfter)
        val (incoming, outgoing) = requests.partition { it.isIncoming() }
        val pendingOutgoing = outgoing.mapNotNull { request -> linkedContactOf(request)?.let { request to it } }
        val pendingOutgoingIds = pendingOutgoing.map { (request, _) -> request.id }.toSet()
        val settledOutgoing = if (changedAfter == null) emptyList() else outgoing.filter { it.id !in pendingOutgoingIds }

        return ChatStateForSync(
            blockStates = blockStates(changedAfter),
            processedIncomingRequests = incoming.mapNotNull { it.toProcessedIncomingRequest() },
            pendingOutgoingRequests = pendingOutgoing.mapNotNull { (request, contact) -> toPendingOutgoingRequest(request, contact) },
            outgoingRequestUpdates = settledOutgoing.map { it.toOutgoingRequestUpdate() },
            contactDevices = contactDevicesRepository.getDevicesForSync(changedAfter),
        )
    }

    override suspend fun getSettledPayments(updatedAfter: Instant?): List<SettledPayment> {
        return chatMessageRepository.getPaymentsUpdatedAfter(updatedAfter).mapNotNull { it.toSettledPayment() }
    }

    override fun observeChatStateChanges(): Flow<Unit> {
        return merge(chatRequestRepository.observeStatusChanges(), contactDevicesRepository.observeModifications())
    }

    private fun ChatMessage.toSettledPayment(): SettledPayment? {
        val payment = content as? ChatMessage.Content.CoinagePayment ?: return null
        val contact = chatId.chatVariant() as? ChatVariant.Contact ?: return null

        val outcome = when (val status = payment.status) {
            is PaymentStatus.Transferred -> SettledPayment.Outcome.Claimed(partial = status.amount.takeIf { it < payment.totalValue })
            PaymentStatus.FailedDetection, PaymentStatus.FailedTransfer -> SettledPayment.Outcome.Failed
            PaymentStatus.Detecting, is PaymentStatus.Detected, is PaymentStatus.PartiallyClaimed -> return null
        }

        return SettledPayment(contactAccountId = contact.contactAccountId, messageId = id, outcome = outcome)
    }

    private suspend fun blockStates(changedAfter: Instant?): List<ChatStateForSync.BlockState> {
        val contacts = if (changedAfter == null) contactsRepository.getBlockedContacts() else contactsRepository.getBlockChangedAfter(changedAfter)
        return contacts.map { ChatStateForSync.BlockState(it.accountId, it.isBlocked) }
    }

    private suspend fun linkedContactOf(request: ChatRequest): Contact? {
        val peer = request.peer ?: return null
        return contactsRepository.getContact(peer)?.takeIf { it.pendingChatRequestId == request.id }
    }

    private suspend fun toPendingOutgoingRequest(request: ChatRequest, contact: Contact): ChatStateForSync.OutgoingRequest? {
        return payloadLoader.load(contact, request)
            .flatMap { outgoingChatRequestService.composeRequestContent(it) }
            .onFailure { Timber.w(it, "Failed to compose pending outgoing request ${request.id} for sync") }
            .getOrNull()
            ?.let { content ->
                ChatStateForSync.OutgoingRequest(request.id, contact.accountId, request.timestamp, BinaryScale.encodeToByteArray(content))
            }
    }

    private fun ChatRequest.toProcessedIncomingRequest(): ChatStateForSync.IncomingRequest? {
        val peer = peer ?: return null
        if (status == ChatRequest.Status.PENDING) return null

        return ChatStateForSync.IncomingRequest(id, peer, timestamp, isAccepted = status == ChatRequest.Status.ACCEPTED)
    }

    private fun ChatRequest.toOutgoingRequestUpdate(): ChatStateForSync.OutgoingRequestUpdate {
        return ChatStateForSync.OutgoingRequestUpdate(id, isCancelled = status != ChatRequest.Status.ACCEPTED)
    }
}
