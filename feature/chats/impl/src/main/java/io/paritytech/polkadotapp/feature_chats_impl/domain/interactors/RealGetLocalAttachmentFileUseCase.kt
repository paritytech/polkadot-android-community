package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.paritytech.polkadotapp.common.data.storage.file.ContentResolver
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.GetLocalAttachmentFileUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.GetLocalAttachmentFileUseCase.LocalAttachmentFile
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Attachment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.AlwaysFailCustomContentDecoder
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.FileDownloadRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RealGetLocalAttachmentFileUseCase @Inject constructor(
    private val chatMessageRepository: ChatMessageRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val contentResolver: ContentResolver,
    private val dispatchers: CoroutineDispatchers,
) : GetLocalAttachmentFileUseCase {
    // Attachments live in RichText content; Custom renderers aren't needed here.
    private val customContentDecoder = AlwaysFailCustomContentDecoder()

    override suspend fun get(messageId: ChatMessageId, identifier: DataByteArray): Result<LocalAttachmentFile> =
        withContext(dispatchers.io) {
            runCancellableCatching { locate(messageId, identifier) }
        }

    private suspend fun locate(messageId: ChatMessageId, identifier: DataByteArray): LocalAttachmentFile {
        val attachment = findHostedAttachment(messageId, identifier) ?: return LocalAttachmentFile.NotStored
        val uri = attachment.uri
        val size = uri?.let(contentResolver::querySize)

        return when {
            uri != null && size != null -> LocalAttachmentFile.Stored(uri, size)
            fileDownloadRepository.isPendingOrInProgress(messageId) -> LocalAttachmentFile.Downloading
            else -> LocalAttachmentFile.NotStored
        }
    }

    private suspend fun findHostedAttachment(messageId: ChatMessageId, identifier: DataByteArray): Attachment.Hosted? {
        val message = chatMessageRepository.subscribeMessageById(messageId, customContentDecoder).first()
        val content = message?.content as? ChatMessage.Content.RichText ?: return null

        return content.attachments
            .filterIsInstance<Attachment.Hosted>()
            .firstOrNull { it.identifier == identifier }
    }
}
