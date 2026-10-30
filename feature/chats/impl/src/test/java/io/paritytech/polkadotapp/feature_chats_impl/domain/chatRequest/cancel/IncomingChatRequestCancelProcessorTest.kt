package io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.cancel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequestId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatAcceptanceRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatDraftRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.chatRequest.acceptance.ChatAcceptance
import io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer.ContactChatSigners
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class IncomingChatRequestCancelProcessorTest {
    private val requests = mutableMapOf<ChatRequestId, ChatRequest>()
    private val contacts = mutableMapOf<AccountId, Contact>()
    private val acceptances = mutableMapOf<AccountId, ChatAcceptance>()
    private var senderChatHasConversation = false
    private var senderChatDeleted = false

    private val chatRequestRepository: ChatRequestRepository = mockk()
    private val contactsRepository: ContactsRepository = mockk()
    private val chatAcceptanceRepository: ChatAcceptanceRepository = mockk()
    private val chatMessageRepository: ChatMessageRepository = mockk()
    private val chatDraftRepository: ChatDraftRepository = mockk(relaxUnitFun = true)
    private val chatSigners: ContactChatSigners = mockk()

    private val processor = IncomingChatRequestCancelProcessor(
        chatRequestRepository, contactsRepository, chatAcceptanceRepository, chatMessageRepository, chatDraftRepository, chatSigners,
    )

    @Before
    fun setUp() {
        coEvery { chatRequestRepository.getById(any()) } answers { requests[firstArg()] }
        coEvery { chatRequestRepository.save(any()) } answers { firstArg<ChatRequest>().let { requests[it.id] = it } }
        coEvery { chatRequestRepository.updateStatus(any(), any()) } answers {
            requests[firstArg()] = requests.getValue(firstArg()).copy(status = secondArg())
        }
        coEvery { contactsRepository.getContact(any()) } answers { contacts[firstArg()] }
        coEvery { contactsRepository.deleteContact(any()) } answers { contacts.remove(firstArg()) }
        val transaction = slot<suspend () -> Boolean>()
        coEvery { contactsRepository.withTransaction(capture(transaction)) } coAnswers { transaction.captured() }
        coEvery { chatAcceptanceRepository.get(any()) } answers { acceptances[firstArg()] }
        coEvery { chatMessageRepository.hasConversationMessages(SENDER_CHAT) } answers { senderChatHasConversation }
        coEvery { chatMessageRepository.deleteAllChatMessages(SENDER_CHAT) } answers { senderChatDeleted = true }
        coEvery { chatSigners.release(any()) } returns Result.success(Unit)
    }

    @Test
    fun `a cancel of an unknown request is remembered and leaves the sender's chat alone`() = runTest {
        contacts[SENDER] = contact(pendingRequestId = null)

        processor.process(SENDER, REQUEST_ID, cancelTimestamp = 2_000)

        val remembered = requests.getValue(REQUEST_ID)
        assertEquals(ChatRequest.Status.CANCELLED, remembered.status)
        assertEquals(ChatRequest.Direction.INCOMING, remembered.direction)
        assertNotNull(contacts[SENDER])
        assertFalse(senderChatDeleted)
    }

    @Test
    fun `a cancel of a pending request removes its contact and chat`() = runTest {
        requests[REQUEST_ID] = incomingRequest(ChatRequest.Status.PENDING)
        contacts[SENDER] = contact(pendingRequestId = REQUEST_ID)

        processor.process(SENDER, REQUEST_ID, cancelTimestamp = 2_000)

        assertEquals(ChatRequest.Status.CANCELLED, requests.getValue(REQUEST_ID).status)
        assertNull(contacts[SENDER])
        assertTrue(senderChatDeleted)
    }

    @Test
    fun `a cancel of a just accepted request with an empty chat removes the chat`() = runTest {
        requests[REQUEST_ID] = incomingRequest(ChatRequest.Status.ACCEPTED)
        contacts[SENDER] = contact(pendingRequestId = null)
        acceptances[SENDER] = acceptance(REQUEST_ID)

        processor.process(SENDER, REQUEST_ID, cancelTimestamp = 2_000)

        assertEquals(ChatRequest.Status.CANCELLED, requests.getValue(REQUEST_ID).status)
        assertNull(contacts[SENDER])
        assertTrue(senderChatDeleted)
    }

    @Test
    fun `a cancel of an accepted request whose chat has messages keeps the chat`() = runTest {
        requests[REQUEST_ID] = incomingRequest(ChatRequest.Status.ACCEPTED)
        contacts[SENDER] = contact(pendingRequestId = null)
        acceptances[SENDER] = acceptance(REQUEST_ID)
        senderChatHasConversation = true

        processor.process(SENDER, REQUEST_ID, cancelTimestamp = 2_000)

        assertChatKept(ChatRequest.Status.ACCEPTED)
    }

    @Test
    fun `a cancel from someone other than the requester is ignored`() = runTest {
        requests[REQUEST_ID] = incomingRequest(ChatRequest.Status.PENDING)
        contacts[SENDER] = contact(pendingRequestId = "another-request")

        processor.process(SENDER, REQUEST_ID, cancelTimestamp = 2_000)

        assertChatKept(ChatRequest.Status.PENDING)
    }

    @Test
    fun `a cancel no newer than the request is ignored`() = runTest {
        requests[REQUEST_ID] = incomingRequest(ChatRequest.Status.PENDING)
        contacts[SENDER] = contact(pendingRequestId = REQUEST_ID)

        processor.process(SENDER, REQUEST_ID, cancelTimestamp = REQUEST_TIMESTAMP)

        assertChatKept(ChatRequest.Status.PENDING)
    }

    @Test
    fun `a cancel naming our own outgoing request is ignored`() = runTest {
        requests[REQUEST_ID] = incomingRequest(ChatRequest.Status.PENDING).copy(direction = ChatRequest.Direction.OUTGOING)
        contacts[SENDER] = contact(pendingRequestId = REQUEST_ID)

        processor.process(SENDER, REQUEST_ID, cancelTimestamp = 2_000)

        assertChatKept(ChatRequest.Status.PENDING)
    }

    private fun assertChatKept(status: ChatRequest.Status) {
        assertEquals(status, requests.getValue(REQUEST_ID).status)
        assertNotNull(contacts[SENDER])
        assertFalse(senderChatDeleted)
    }

    private fun incomingRequest(status: ChatRequest.Status) = ChatRequest(
        welcomeMessageId = REQUEST_ID,
        timestamp = REQUEST_TIMESTAMP,
        direction = ChatRequest.Direction.INCOMING,
        status = status,
        delivery = ChatRequest.Delivery.DeliveredUntracked,
    )

    private fun contact(pendingRequestId: ChatRequestId?): Contact = mockk {
        every { accountId } returns SENDER
        every { pendingChatRequestId } returns pendingRequestId
    }

    private fun acceptance(requestId: ChatRequestId) = ChatAcceptance(
        contactAccountId = SENDER,
        requestId = requestId,
        messageId = "acceptance-message",
        acceptedAt = Instant.fromEpochMilliseconds(1_500),
        delivery = ChatRequest.Delivery.Undelivered,
    )

    private companion object {
        const val REQUEST_ID = "request-1"
        const val REQUEST_TIMESTAMP = 1_000L
        val SENDER = ByteArray(32) { 7 }.toDataByteArray()
        val SENDER_CHAT = ChatId.fromContact(SENDER)
    }
}
