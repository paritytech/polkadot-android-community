package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.database.dao.FileDownloadDao
import io.paritytech.polkadotapp.database.model.FileDownloadLocal
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.HopTicket
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.FileDownload
import io.paritytech.polkadotapp.feature_chats_impl.domain.models.toLocal
import javax.inject.Inject

class FileDownloadRepository @Inject constructor(
    private val dao: FileDownloadDao
) {
    suspend fun addDownloadToQueue(download: FileDownload) {
        dao.insert(download.toLocal())
    }

    suspend fun getNextPending(): FileDownload? {
        return dao.getNextPending()?.toDomain()
    }

    suspend fun markInProgress(messageId: ChatMessageId) {
        dao.markInProgress(messageId)
    }

    suspend fun saveMetadata(
        messageId: ChatMessageId,
        chunkHashes: List<String>
    ) {
        dao.saveMetadata(
            messageId = messageId,
            chunkHashes = chunkHashes.joinToString(",")
        )
    }

    suspend fun updateProgress(
        messageId: ChatMessageId,
        downloadedChunks: Int,
        filePath: String
    ) {
        dao.updateProgress(
            messageId = messageId,
            downloadedChunks = downloadedChunks,
            filePath = filePath
        )
    }

    suspend fun markDone(messageId: ChatMessageId) {
        dao.markDone(messageId)
    }

    suspend fun markFailed(messageId: ChatMessageId, error: FileDownload.Error) {
        dao.markFailed(messageId, error.category.toLocal(), error.cause)
    }
}

private fun FileDownload.toLocal() = FileDownloadLocal(
    messageId = messageId,
    chatId = chatId.toLocal(),
    identifier = identifier.value,
    ticket = ticket.bytes,
    nodeUrl = nodeUrl,
    mimeType = mimeType,
    filePath = filePath,
    downloadedChunks = progress.downloadedChunks,
    chunkHashes = (progress.metadata as? FileDownload.Metadata.Resolved)?.chunkHashes?.joinToString(","),
    status = progress.status.toLocal(),
    errorCategory = progress.error?.category?.toLocal(),
    errorCause = progress.error?.cause,
    createdAt = createdAt
)

private fun FileDownloadLocal.toDomain() = FileDownload(
    messageId = messageId,
    chatId = ChatId.fromRawValue(chatId),
    identifier = identifier.toDataByteArray(),
    ticket = HopTicket.fromRaw(ticket),
    nodeUrl = nodeUrl,
    mimeType = mimeType,
    filePath = filePath,
    progress = FileDownload.Progress(
        status = status.toDomain(),
        downloadedChunks = downloadedChunks,
        metadata = chunkHashes.toMetadata(),
        error = errorCategory?.let { FileDownload.Error(category = it.toDomain(), cause = errorCause.orEmpty()) }
    ),
    createdAt = createdAt
)

private fun FileDownload.Status.toLocal() = when (this) {
    FileDownload.Status.PENDING -> FileDownloadLocal.Status.PENDING
    FileDownload.Status.IN_PROGRESS -> FileDownloadLocal.Status.IN_PROGRESS
    FileDownload.Status.DONE -> FileDownloadLocal.Status.DONE
    FileDownload.Status.FAILED -> FileDownloadLocal.Status.FAILED
}

private fun FileDownloadLocal.Status.toDomain() = when (this) {
    FileDownloadLocal.Status.PENDING -> FileDownload.Status.PENDING
    FileDownloadLocal.Status.IN_PROGRESS -> FileDownload.Status.IN_PROGRESS
    FileDownloadLocal.Status.DONE -> FileDownload.Status.DONE
    FileDownloadLocal.Status.FAILED -> FileDownload.Status.FAILED
}

private fun FileDownload.Error.Category.toLocal() = when (this) {
    FileDownload.Error.Category.NETWORK -> FileDownloadLocal.ErrorCategory.NETWORK
    FileDownload.Error.Category.HOP_ERROR -> FileDownloadLocal.ErrorCategory.HOP_ERROR
    FileDownload.Error.Category.FILE_WRITE_ERROR -> FileDownloadLocal.ErrorCategory.FILE_WRITE_ERROR
    FileDownload.Error.Category.UNKNOWN -> FileDownloadLocal.ErrorCategory.UNKNOWN
}

private fun FileDownloadLocal.ErrorCategory.toDomain() = when (this) {
    FileDownloadLocal.ErrorCategory.NETWORK -> FileDownload.Error.Category.NETWORK
    FileDownloadLocal.ErrorCategory.HOP_ERROR -> FileDownload.Error.Category.HOP_ERROR
    FileDownloadLocal.ErrorCategory.FILE_WRITE_ERROR -> FileDownload.Error.Category.FILE_WRITE_ERROR
    FileDownloadLocal.ErrorCategory.UNKNOWN -> FileDownload.Error.Category.UNKNOWN
}

private fun String?.toMetadata(): FileDownload.Metadata {
    val hashes = this?.split(",")?.filter { it.isNotEmpty() }
    return if (hashes.isNullOrEmpty()) FileDownload.Metadata.Pending else FileDownload.Metadata.Resolved(hashes)
}
