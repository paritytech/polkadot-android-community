package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.paritytech.polkadotapp.common.data.storage.file.ContentResolver
import io.paritytech.polkadotapp.common.data.storage.file.FileProvider
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.MissingAttachmentFilesUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.MissingAttachmentFilesUseCase.MissingAttachmentFile
import io.paritytech.polkadotapp.feature_chats_api.domain.model.Attachment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.ChatMessageAttachmentUpdater
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.AlwaysFailCustomContentDecoder
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_chats_impl.data.storage.AttachmentFileStorage
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RealMissingAttachmentFilesUseCase @Inject constructor(
    private val chatMessageRepository: ChatMessageRepository,
    private val attachmentFileStorage: AttachmentFileStorage,
    private val messageAttachmentUpdater: ChatMessageAttachmentUpdater,
    private val contentResolver: ContentResolver,
    private val fileProvider: FileProvider,
    private val dispatchers: CoroutineDispatchers,
) : MissingAttachmentFilesUseCase {
    private val customContentDecoder = AlwaysFailCustomContentDecoder()

    override suspend fun getMissing(): Result<List<MissingAttachmentFile>> = withContext(dispatchers.io) {
        runCancellableCatching {
            chatMessageRepository.getRichTextMessages().flatMap(::missingFiles)
        }
    }

    override suspend fun attachReceived(file: MissingAttachmentFile): Result<Unit> = runCancellableCatching {
        if (messageExists(file)) {
            messageAttachmentUpdater.updateReceivedAttachment(
                chatId = file.chatId,
                messageId = file.messageId,
                identifier = file.identifier,
                uri = fileProvider.uriOf(file.destination),
            )
        } else {
            file.destination.delete()
        }
    }

    private suspend fun messageExists(file: MissingAttachmentFile): Boolean {
        return chatMessageRepository.getMessageById(file.chatId, file.messageId, customContentDecoder) != null
    }

    private fun missingFiles(message: ChatMessage): List<MissingAttachmentFile> {
        val content = message.content as? ChatMessage.Content.RichText ?: return emptyList()

        return content.attachments
            .filterIsInstance<Attachment.Hosted>()
            .filter { attachment -> attachment.uri?.let(contentResolver::querySize) == null }
            .map { attachment ->
                MissingAttachmentFile(
                    chatId = message.chatId,
                    messageId = message.id,
                    identifier = attachment.identifier,
                    destination = attachmentFileStorage.getAttachmentDownloadFile(
                        messageId = message.id,
                        identifier = attachment.identifier,
                        mimeType = attachment.meta.mimeType,
                    ),
                )
            }
    }
}
