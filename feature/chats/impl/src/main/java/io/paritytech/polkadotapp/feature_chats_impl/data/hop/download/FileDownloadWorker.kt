package io.paritytech.polkadotapp.feature_chats_impl.data.hop.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.common.utils.toWorkerResult
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.HopTransfer
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.HopTransferPassLocks
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.TransferQueueProcessor
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.hopTransferForegroundInfo
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.scheduleTransferWakeUp
import timber.log.Timber
import io.paritytech.polkadotapp.common.R as RCommon

@HiltWorker
class FileDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val transferQueueProcessor: TransferQueueProcessor,
    private val transferQueue: FileDownloadTransferQueue,
    private val passLocks: HopTransferPassLocks
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        runCancellableCatching { setForeground(getForegroundInfo()) }
            .onFailure { Timber.d(it, "Failed to promote download worker to foreground; continuing as background work") }

        val processed = passLocks.runPass(HopTransfer.DOWNLOAD) {
            runCancellableCatching { transferQueueProcessor.processPending(transferQueue) }
        }

        scheduleTransferWakeUp(applicationContext, HopTransfer.DOWNLOAD, transferQueue.soonestNextAttemptAt())

        return processed.toWorkerResult(retryOnFailure = true)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = hopTransferForegroundInfo(
        context = applicationContext,
        notificationId = NOTIFICATION_ID,
        titleRes = RCommon.string.chat_file_download_worker_notification_title,
        messageRes = RCommon.string.chat_file_download_worker_notification_message
    )

    companion object {
        const val WORK_ID = "HopFileDownload"
        private const val NOTIFICATION_ID = 1001
    }
}
