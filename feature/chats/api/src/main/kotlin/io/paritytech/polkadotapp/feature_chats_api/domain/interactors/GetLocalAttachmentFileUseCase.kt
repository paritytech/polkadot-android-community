package io.paritytech.polkadotapp.feature_chats_api.domain.interactors

import android.net.Uri
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId

interface GetLocalAttachmentFileUseCase {
    sealed interface LocalAttachmentFile {
        /** The full file is on this device: an own original or a completed download. */
        class Stored(val uri: Uri, val size: InformationSize) : LocalAttachmentFile

        /** Not on this device yet, but a download of it is pending or running. */
        data object Downloading : LocalAttachmentFile

        data object NotStored : LocalAttachmentFile
    }

    /**
     * Looks up the local copy of the hosted attachment [identifier] (`P2PMixnetFile.identifier`) of message
     * [messageId], for example to serve it to an own device.
     */
    suspend fun get(messageId: ChatMessageId, identifier: DataByteArray): Result<LocalAttachmentFile>
}
