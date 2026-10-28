package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import android.net.Uri
import io.paritytech.polkadotapp.common.data.storage.file.ContentResolver
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.GetLocalAttachmentFileUseCase.LocalAttachmentFile
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Attachment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.HopTicket
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.FileDownloadRepository
import io.paritytech.polkadotapp.test_shared.TestCoroutineDispatchers
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class RealGetLocalAttachmentFileUseCaseTest {
    private val messageId = "m1"
    private val identifier = byteArrayOf(1, 2).toDataByteArray()
    private val uri: Uri = mock(Uri::class.java)

    private val chatMessageRepository: ChatMessageRepository = mock(ChatMessageRepository::class.java)
    private val fileDownloadRepository: FileDownloadRepository = mock(FileDownloadRepository::class.java)
    private val contentResolver: ContentResolver = mock(ContentResolver::class.java)

    private val useCase = RealGetLocalAttachmentFileUseCase(
        chatMessageRepository = chatMessageRepository,
        fileDownloadRepository = fileDownloadRepository,
        contentResolver = contentResolver,
        dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    @Test
    fun `attachment with a readable local file is stored`() = runBlocking<Unit> {
        withMessage(attachment(identifier, uri))
        withFileSize(3)

        val file = useCase.get(messageId, identifier).getOrThrow()

        assertTrue(file is LocalAttachmentFile.Stored)
        file as LocalAttachmentFile.Stored
        assertSame(uri, file.uri)
        assertEquals(3.bytes, file.size)
    }

    @Test
    fun `attachment without a local file is downloading while its download is active`() = runBlocking<Unit> {
        withMessage(attachment(identifier, uri = null))
        withDownloadActive(true)

        assertEquals(LocalAttachmentFile.Downloading, useCase.get(messageId, identifier).getOrThrow())
    }

    @Test
    fun `attachment whose local file is gone is not stored`() = runBlocking<Unit> {
        withMessage(attachment(identifier, uri))
        withFileSize(null)
        withDownloadActive(false)

        assertEquals(LocalAttachmentFile.NotStored, useCase.get(messageId, identifier).getOrThrow())
    }

    @Test
    fun `other attachment of the message is not stored`() = runBlocking<Unit> {
        withMessage(attachment(byteArrayOf(9).toDataByteArray(), uri))

        assertEquals(LocalAttachmentFile.NotStored, useCase.get(messageId, identifier).getOrThrow())
    }

    @Test
    fun `unknown message is not stored`() = runBlocking<Unit> {
        whenever(chatMessageRepository.subscribeMessageById(eq(messageId), any())).thenReturn(flowOf(null))

        assertEquals(LocalAttachmentFile.NotStored, useCase.get(messageId, identifier).getOrThrow())
    }

    private fun withMessage(attachment: Attachment) {
        val message = mock(ChatMessage::class.java)
        whenever(message.content).thenReturn(ChatMessage.Content.RichText(text = null, attachments = listOf(attachment)))
        whenever(chatMessageRepository.subscribeMessageById(eq(messageId), any())).thenReturn(flowOf(message))
    }

    private fun withFileSize(bytes: Long?) {
        whenever(contentResolver.querySize(uri)).thenReturn(bytes?.bytes)
    }

    private suspend fun withDownloadActive(active: Boolean) {
        whenever(fileDownloadRepository.isPendingOrInProgress(messageId)).thenReturn(active)
    }

    private fun attachment(identifier: DataByteArray, uri: Uri?) = Attachment.Hosted(
        uri = uri,
        identifier = identifier,
        ticket = HopTicket.fromRaw(ByteArray(32)),
        nodeUrl = "wss://hop.example/node",
        meta = Attachment.Meta.General(fileName = "file.pdf", mimeType = "application/pdf", size = 3.bytes),
    )
}
