package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Contact
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatRequestRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RealRemoveContactUseCaseTest {
    private val peer = ByteArray(32) { 5 }.toDataByteArray()

    private val contactsRepository: ContactsRepository = mockk(relaxUnitFun = true)
    private val chatRequestRepository: ChatRequestRepository = mockk(relaxUnitFun = true)

    private val useCase = RealRemoveContactUseCase(contactsRepository, mockk(relaxUnitFun = true), chatRequestRepository)

    @Test
    fun `removing a contact with a pending outgoing request records the request as cancelled`() = runTest {
        val contact: Contact = mockk { every { pendingChatRequestId } returns "out-1" }
        coEvery { contactsRepository.getContact(peer) } returns contact
        coEvery { chatRequestRepository.getById("out-1") } returns ChatRequest(
            welcomeMessageId = "out-1",
            peer = peer,
            timestamp = 1_000L,
            direction = ChatRequest.Direction.OUTGOING,
            status = ChatRequest.Status.PENDING,
            delivery = ChatRequest.Delivery.DeliveredUntracked,
        )

        useCase.removeContact(peer)

        coVerify { chatRequestRepository.recordCancellation("out-1") }
    }
}
