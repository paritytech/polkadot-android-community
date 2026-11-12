package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.MissingAttachmentFilesUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.MissingAttachmentFilesUseCase.MissingAttachmentFile
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFile
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileOutcome
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileRequest
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileAvailabilityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.SyncFileRequester
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class DeviceTransferFilePuller @Inject constructor(
    private val missingAttachmentFilesUseCase: MissingAttachmentFilesUseCase,
) {
    private class AvailableFile(val file: MissingAttachmentFile, val size: InformationSize)

    private companion object {
        const val FILES_PER_REQUEST = 64
    }

    suspend fun pull(
        requester: SyncFileRequester,
        onProgress: (DeviceTransferProgress.Files) -> Unit,
    ): Result<Unit> = missingAttachmentFilesUseCase.getMissing().map { missing ->
        val (received, notReceived) = missing.partition { it.destination.exists() }
        received.forEach { attach(it) }

        val available = availableFiles(requester, notReceived)
        Timber.d("Device transfer: ${available.size} of ${notReceived.size} missing files are on the old phone")

        pullAvailable(requester, available, onProgress)
    }

    private suspend fun availableFiles(requester: SyncFileRequester, missing: List<MissingAttachmentFile>): List<AvailableFile> {
        return missing.chunked(FILES_PER_REQUEST).flatMap { batch ->
            val availability = requester.requestAvailability(batch.map { it.toSiblingFile() })

            batch.zip(availability).mapNotNull { (file, entry) ->
                (entry as? FileAvailabilityScale.Available)?.let { AvailableFile(file, it.totalSize.toLong().bytes) }
            }
        }
    }

    private suspend fun pullAvailable(
        requester: SyncFileRequester,
        available: List<AvailableFile>,
        onProgress: (DeviceTransferProgress.Files) -> Unit,
    ) {
        val totalBytes = available.sumOf { it.size.inWholeBytes }.bytes
        val receivedBytes = AtomicLong(0)
        onProgress(DeviceTransferProgress.Files(receivedBytes = InformationSize.ZERO, totalBytes = totalBytes))

        val completedCount = available.chunked(FILES_PER_REQUEST).sumOf { batch ->
            pullBatch(requester, batch) { bytes ->
                val received = receivedBytes.addAndGet(bytes.inWholeBytes).bytes
                onProgress(DeviceTransferProgress.Files(receivedBytes = minOf(received, totalBytes), totalBytes = totalBytes))
            }
        }
        Timber.d("Device transfer: pulled $completedCount of ${available.size} available files")
    }

    private suspend fun pullBatch(
        requester: SyncFileRequester,
        batch: List<AvailableFile>,
        onBytes: (InformationSize) -> Unit,
    ): Int {
        val requests = batch.map { SiblingFileRequest(it.file.toSiblingFile(), it.file.destination) }
        val completed = batch.zip(requester.request(requests, onBytes))
            .filter { (_, outcome) -> outcome == SiblingFileOutcome.Completed }
            .map { (available, _) -> available.file }

        completed.forEach { attach(it) }

        return completed.size
    }

    private suspend fun attach(file: MissingAttachmentFile) {
        missingAttachmentFilesUseCase.attachReceived(file)
            .logFailure("Device transfer: failed to attach the received file of message ${file.messageId}")
    }

    private fun MissingAttachmentFile.toSiblingFile(): SiblingFile = SiblingFile.MessageAttachment(messageId, identifier)
}
