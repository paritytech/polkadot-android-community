package io.paritytech.polkadotapp.feature_chats_impl.data.hop.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.HopTransferWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.enqueueExpeditedDrain
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.FileDownloadRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.FileDownload
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.toFileDownloadError
import io.paritytech.polkadotapp.common.R as RCommon

@HiltWorker
class FileDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val fileDownloadRepository: FileDownloadRepository,
    private val hopFileDownloader: HopFileDownloader
) : HopTransferWorker<FileDownload>(appContext, parameters) {
    override val notificationId = NOTIFICATION_ID
    override val notificationTitleRes = RCommon.string.chat_file_download_worker_notification_title
    override val notificationMessageRes = RCommon.string.chat_file_download_worker_notification_message

    override suspend fun nextPending(): FileDownload? = fileDownloadRepository.getNextPending()
    override suspend fun markInProgress(item: FileDownload) = fileDownloadRepository.markInProgress(item.messageId)
    override suspend fun process(item: FileDownload) = hopFileDownloader.download(item)
    override suspend fun markDone(item: FileDownload) = fileDownloadRepository.markDone(item.messageId)
    override suspend fun markFailed(item: FileDownload, error: Throwable) =
        fileDownloadRepository.markFailed(item.messageId, error.toFileDownloadError())

    companion object {
        private const val WORK_ID = "HopFileDownload"
        private const val NOTIFICATION_ID = 1001

        fun startDownloadingWork(context: Context) = enqueueExpeditedDrain<FileDownloadWorker>(context, WORK_ID)
    }
}
