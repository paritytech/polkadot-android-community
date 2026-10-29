package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestPayload
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.OutgoingChatRequestService
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.delivery.ChatRequestPayloadLoader
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.RequestContentV1
import io.paritytech.polkadotapp.feature_chats_transport_protocol.scale.VersionedRequestContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class RealChatStateSyncUseCaseTest {
    private val alice = account(1)
    private val bob = account(2)
    private val carol = account(3)
    private val dave = account(4)
    private val erin = account(5)
    private val frank = account(6)
    private val gina = account(7)

    private val payload: OutgoingChatRequestPayload = mockk()
    private val contactsRepository: ContactsRepository = mockk()
    private val chatRequestRepository: ChatRequestRepository = mockk()
    private val payloadLoader: ChatRequestPayloadLoader = mockk()
    private val outgoingService: OutgoingChatRequestService = mockk()

    private val useCase = RealChatStateSyncUseCase(contactsRepository, chatRequestRepository, payloadLoader, outgoingService)

    @Before
    fun setUp() {
        coEvery { payloadLoader.load(any(), any()) } returns Result.success(payload)
        coEvery { outgoingService.composeRequestContent(payload) } returns
            Result.success(VersionedRequestContent.V1(RequestContentV1(pushToken = null, welcomeMessage = null)))
        coEvery { contactsRepository.getContact(erin) } returns contact(erin, pendingRequestId = "out-pending")
        coEvery { contactsRepository.getContact(frank) } returns contact(frank, pendingRequestId = null)
        coEvery { contactsRepository.getContact(gina) } returns null
    }

    @Test
    fun `full state holds blocked contacts, processed incoming requests and pending outgoing ones`() = runTest {
        coEvery { contactsRepository.getBlockedContacts() } returns listOf(contact(carol, isBlocked = true))
        coEvery { chatRequestRepository.getAll() } returns listOf(
            request("in-accepted", alice, ChatRequest.Direction.INCOMING, ChatRequest.Status.ACCEPTED),
            request("in-declined", bob, ChatRequest.Direction.INCOMING, ChatRequest.Status.DECLINED),
            request("in-pending", dave, ChatRequest.Direction.INCOMING, ChatRequest.Status.PENDING),
            request("in-no-peer", null, ChatRequest.Direction.INCOMING, ChatRequest.Status.ACCEPTED),
            request("out-pending", erin, ChatRequest.Direction.OUTGOING, ChatRequest.Status.PENDING),
            request("out-accepted", frank, ChatRequest.Direction.OUTGOING, ChatRequest.Status.ACCEPTED),
        )

        val state = useCase.getChatState(changedAfter = null)

        assertEquals(listOf(carol to true), state.blockStates.map { it.accountId to it.isBlocked })
        assertEquals(
            listOf(Triple("in-accepted", alice, true), Triple("in-declined", bob, false)),
            state.processedIncomingRequests.map { Triple(it.id, it.peer, it.isAccepted) }
        )
        assertEquals(listOf("out-pending" to "000000"), state.pendingOutgoingRequests.map { it.id to it.encodedContent.toHexString() })
        assertEquals(emptyList<String>(), state.outgoingRequestUpdates.map { it.id })
    }

    @Test
    fun `changed state reports outgoing requests that were accepted or cancelled`() = runTest {
        val checkpoint = Instant.fromEpochMilliseconds(500)
        coEvery { contactsRepository.getBlockChangedAfter(checkpoint) } returns listOf(contact(carol, isBlocked = false))
        coEvery { chatRequestRepository.getStatusChangedAfter(checkpoint) } returns listOf(
            request("out-pending", erin, ChatRequest.Direction.OUTGOING, ChatRequest.Status.PENDING),
            request("out-accepted", frank, ChatRequest.Direction.OUTGOING, ChatRequest.Status.ACCEPTED),
            request("out-cancelled", gina, ChatRequest.Direction.OUTGOING, ChatRequest.Status.PENDING),
        )

        val state = useCase.getChatState(changedAfter = checkpoint)

        assertEquals(listOf(carol to false), state.blockStates.map { it.accountId to it.isBlocked })
        assertEquals(listOf("out-pending"), state.pendingOutgoingRequests.map { it.id })
        assertEquals(
            listOf("out-accepted" to false, "out-cancelled" to true),
            state.outgoingRequestUpdates.map { it.id to it.isCancelled }
        )
    }

    private fun account(seed: Byte): AccountId = ByteArray(32) { seed }.toDataByteArray()

    private fun request(
        id: String,
        peer: AccountId?,
        direction: ChatRequest.Direction,
        status: ChatRequest.Status,
    ) = ChatRequest(
        welcomeMessageId = id,
        peer = peer,
        timestamp = 1_000L,
        direction = direction,
        status = status,
        delivery = ChatRequest.Delivery.DeliveredUntracked,
    )

    private fun contact(id: AccountId, pendingRequestId: String? = null, isBlocked: Boolean = false): Contact = mockk {
        every { accountId } returns id
        every { pendingChatRequestId } returns pendingRequestId
        every { this@mockk.isBlocked } returns isBlocked
    }
}
