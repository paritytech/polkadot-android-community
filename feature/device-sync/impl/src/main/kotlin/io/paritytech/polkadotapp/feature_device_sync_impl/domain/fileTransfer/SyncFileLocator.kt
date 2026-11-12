package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import io.paritytech.polkadotapp.common.data.storage.file.ContentResolver
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.GetLocalAttachmentFileUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.GetLocalAttachmentFileUseCase.LocalAttachmentFile
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileReferenceScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileUnavailableReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.MessageAttachmentIdScale
import java.io.InputStream
import javax.inject.Inject

sealed interface SyncFileLocation {
    class Stored(val size: InformationSize, val open: () -> InputStream?) : SyncFileLocation

    class Unavailable(val reason: FileUnavailableReasonScale) : SyncFileLocation
}

interface SyncFileLocator {
    suspend fun locate(file: FileReferenceScale): SyncFileLocation
}

class RealSyncFileLocator @Inject constructor(
    private val getLocalAttachmentFileUseCase: GetLocalAttachmentFileUseCase,
    private val contentResolver: ContentResolver,
) : SyncFileLocator {
    override suspend fun locate(file: FileReferenceScale): SyncFileLocation = when (file) {
        is FileReferenceScale.MessageAttachment -> locateAttachment(file.attachment)
    }

    private suspend fun locateAttachment(attachment: MessageAttachmentIdScale): SyncFileLocation {
        return getLocalAttachmentFileUseCase.get(attachment.messageId, attachment.identifier.toDataByteArray())
            .logFailure("SyncFileLocator: failed to look up attachment of message ${attachment.messageId}")
            .fold(
                onSuccess = ::toLocation,
                onFailure = { SyncFileLocation.Unavailable(FileUnavailableReasonScale.NOT_STORED) }
            )
    }

    private fun toLocation(file: LocalAttachmentFile): SyncFileLocation = when (file) {
        is LocalAttachmentFile.Stored -> SyncFileLocation.Stored(file.size) { contentResolver.openInputStream(file.uri) }
        LocalAttachmentFile.Downloading -> SyncFileLocation.Unavailable(FileUnavailableReasonScale.DOWNLOADING)
        LocalAttachmentFile.NotStored -> SyncFileLocation.Unavailable(FileUnavailableReasonScale.NOT_STORED)
    }
}
