package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.compaction.CompactionExpansionWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.download.FileDownloadWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload.FileUploadWorker

@HiltWorker
class HopTransferWakeUpWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val transfer = HopTransfer.entries.find { it.wakeUpWorkId == inputData.getString(KEY_TRANSFER) } ?: return Result.failure()

        when (transfer) {
            HopTransfer.UPLOAD -> FileUploadWorker.startUploadingWork(applicationContext)
            HopTransfer.DOWNLOAD -> FileDownloadWorker.startDownloadingWork(applicationContext)
            HopTransfer.COMPACTION_EXPANSION -> CompactionExpansionWorker.startExpansionWork(applicationContext)
        }

        return Result.success()
    }

    companion object {
        private const val KEY_TRANSFER = "transfer"

        fun inputFor(transfer: HopTransfer): Data = workDataOf(KEY_TRANSFER to transfer.wakeUpWorkId)
    }
}
