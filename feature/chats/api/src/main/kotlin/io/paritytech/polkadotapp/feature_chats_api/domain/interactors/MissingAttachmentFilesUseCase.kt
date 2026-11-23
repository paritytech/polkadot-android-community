package io.paritytech.polkadotapp.feature_chats_api.domain.interactors

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import java.io.File

interface MissingAttachmentFilesUseCase {
    class MissingAttachmentFile(
        val chatId: ChatId,
        val messageId: ChatMessageId,
        val identifier: DataByteArray,
        val destination: File,
    )

    suspend fun getMissing(): Result<List<MissingAttachmentFile>>

    suspend fun attachReceived(file: MissingAttachmentFile): Result<Unit>
}
