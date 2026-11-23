package io.paritytech.polkadotapp.feature_chats_api.domain.interactors

import android.net.Uri
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId

interface GetLocalAttachmentFileUseCase {
    sealed interface LocalAttachmentFile {
        class Stored(val uri: Uri, val size: InformationSize) : LocalAttachmentFile

        data object Downloading : LocalAttachmentFile

        data object NotStored : LocalAttachmentFile
    }

    suspend fun get(messageId: ChatMessageId, identifier: DataByteArray): Result<LocalAttachmentFile>
}
