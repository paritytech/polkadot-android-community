package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.renewal

import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flattenResult
import io.paritytech.polkadotapp.common.utils.forEachAsync
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.mapAsync
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ContactWithChatRequest
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestService
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ALLOCATION_WAIT_TIMEOUT
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestDeliveryAccount
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestDeliveryKeypairDerivation
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestDeliverySigners
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestPayloadLoader
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestPublisher
import io.paritytech.polkadotapp.feature_statement_store_api.domain.notificationAllocator.NotificationStatementAccountAllocator
import timber.log.Timber
import javax.inject.Inject

class ChatRequestRenewalCandidate(
    val contact: Contact,
    val request: ChatRequest,
    val deliveredPeriod: UInt,
)

/**
 * Keeps not-yet-accepted, anonymously delivered chat requests on the statement store. A request from an earlier
 * period gets a fresh account and slot in the current one and is published again from it, while its old copy lives
 * out the grace window; a request of the current period that went missing is published again from the same account.
 */
class ChatRequestRenewer @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val allocator: NotificationStatementAccountAllocator,
    private val keypairDerivation: ChatRequestDeliveryKeypairDerivation,
    private val outgoingChatRequestService: OutgoingChatRequestService,
    private val payloadLoader: ChatRequestPayloadLoader,
    private val publisher: ChatRequestPublisher,
    private val signers: ChatRequestDeliverySigners,
) {
    suspend fun renew(): Result<Unit> {
        return allocator.isSupported().flatMap { supported ->
            if (!supported) return@flatMap Result.success(Unit)

            renewCandidates(contactsRepository.getContactsWithAnonymouslyDeliveredPendingRequests().mapNotNull(::toCandidate))
        }
    }

    private suspend fun renewCandidates(candidates: List<ChatRequestRenewalCandidate>): Result<Unit> {
        val period = allocator.currentPeriod()
        val (stale, current) = candidates.partition { it.deliveredPeriod < period }
        Timber.i("Chat request renewal: ${stale.size} from earlier periods, ${current.size} of period $period")

        return renewStale(stale, period).flatMap { resendMissing(current) }
    }

    private suspend fun renewStale(stale: List<ChatRequestRenewalCandidate>, period: UInt): Result<Unit> {
        if (stale.isEmpty()) return Result.success(Unit)

        return stale.map { keypairDerivation.deliveryAccount(it.request.id, period) }.flattenResult()
            .flatMap { accounts -> claimSlots(stale.zip(accounts)) }
            .map { claimed -> claimed.forEachAsync { (candidate, account) -> publishOnceAllocated(candidate, account) } }
    }

    private suspend fun claimSlots(
        renewals: List<Pair<ChatRequestRenewalCandidate, ChatRequestDeliveryAccount>>,
    ): Result<List<Pair<ChatRequestRenewalCandidate, ChatRequestDeliveryAccount>>> {
        return allocator.allocateAll(renewals.map { (_, account) -> account.accountId }).map { claimed ->
            renewals.filter { (_, account) -> account.accountId in claimed }
        }
    }

    private suspend fun publishOnceAllocated(candidate: ChatRequestRenewalCandidate, account: ChatRequestDeliveryAccount) {
        allocator.awaitAllocated(account.accountId, ALLOCATION_WAIT_TIMEOUT)
            .flatMap { publishFrom(candidate, account) }
            .logFailure("Chat request ${candidate.request.id}: renewal into period ${account.period} failed")
    }

    private suspend fun resendMissing(current: List<ChatRequestRenewalCandidate>): Result<Unit> {
        return current.mapAsync { candidate -> resendIfMissing(candidate) }.flattenResult().coerceToUnit()
    }

    private suspend fun resendIfMissing(candidate: ChatRequestRenewalCandidate): Result<Unit> {
        return keypairDerivation.deliveryAccount(candidate.request.id, candidate.deliveredPeriod).flatMap { account ->
            outgoingChatRequestService.isStoredBy(candidate.contact, account.accountId).flatMap { stored ->
                if (stored) Result.success(Unit) else publishFrom(candidate, account)
            }
        }.logFailure("Chat request ${candidate.request.id}: presence check or re-send failed")
    }

    private suspend fun publishFrom(candidate: ChatRequestRenewalCandidate, account: ChatRequestDeliveryAccount): Result<Unit> {
        return payloadLoader.load(candidate.contact, candidate.request).flatMap { payload ->
            publisher.republish(candidate.request, payload, signers.anonymousSigner(account))
        }
    }

    private fun toCandidate(entry: ContactWithChatRequest): ChatRequestRenewalCandidate? {
        val request = entry.pendingChatRequest ?: return null
        val delivery = request.delivery as? ChatRequest.Delivery.DeliveredAnonymously ?: return null

        return ChatRequestRenewalCandidate(entry.contact, request, delivery.period)
    }
}
