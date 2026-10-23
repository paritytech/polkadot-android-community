package io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload

import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.ConcurrentTransferQueue
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.RetryDecision
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.FileUploadRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.FileUpload
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.toFileUploadError
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.time.Instant

class FileUploadTransferQueue @Inject constructor(
    private val repository: FileUploadRepository,
    private val uploader: HopFileUploader,
    uploadConcurrency: UploadConcurrency
) : ConcurrentTransferQueue<FileUpload> {
    override val maxInFlight: Int = uploadConcurrency.maxInFlight()

    override suspend fun nextPending(excluding: Collection<FileUpload>): FileUpload? =
        repository.getNextPending(excluding.map { it.messageId })

    override fun queueChanges(): Flow<Unit> = repository.observeQueueChanges()
    override suspend fun nextBackoffEndAt(): Instant? = repository.getNextBackoffEndAt()
    override suspend fun markInProgress(item: FileUpload) = repository.markInProgress(item.messageId)
    override suspend fun process(item: FileUpload) = uploader.upload(item)
    override suspend fun markDone(item: FileUpload) = repository.markDone(item.messageId)
    override suspend fun markFailed(item: FileUpload, error: Throwable) =
        repository.markFailed(item.messageId, error.toFileUploadError())

    override suspend fun scheduleRetry(item: FileUpload, reschedule: RetryDecision.Reschedule) =
        repository.scheduleRetry(item.messageId, reschedule.attemptCount, reschedule.firstFailureAt, reschedule.nextAttemptAt)

    override suspend fun soonestNextAttemptAt(): Instant? = repository.getSoonestNextAttemptAt()
}
