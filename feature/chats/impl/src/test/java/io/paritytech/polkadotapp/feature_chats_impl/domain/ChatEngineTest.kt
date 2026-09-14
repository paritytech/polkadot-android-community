package io.paritytech.polkadotapp.feature_chats_impl.domain

import io.mockk.every
import io.mockk.mockk
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ContactsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatEngineTest {
    private val contactsRepository: ContactsRepository = mockk()

    private val chatEngine = ChatEngine(
        chatMessageRepository = mockk(),
        chatMessageProcessingRepository = mockk(),
        chatExtensionRegistry = mockk(),
        accountRepository = mockk(),
        contactsRepository = contactsRepository,
        messageRevisionRepository = mockk(),
        dmChatMessageOriginDisplayResolverFactory = mockk(),
        chatDisplayGenerator = mockk(),
        messageSaveProcessors = emptySet(),
        headerRenderers = emptyMap(),
        originConfigurations = emptyMap(),
        chatRoomRepository = mockk(),
        deleteRoomUseCase = mockk(),
    )

    @Test
    fun `observing display of a removed contact emits failure`() = runTest {
        val contactId = byteArrayOf(1).intoAccountId()
        every { contactsRepository.subscribeContact(contactId) } returns flowOf(null)

        val display = chatEngine.observeChatDisplay(ChatId.fromContact(contactId)).first()

        assertTrue(display.isFailure)
    }
}
