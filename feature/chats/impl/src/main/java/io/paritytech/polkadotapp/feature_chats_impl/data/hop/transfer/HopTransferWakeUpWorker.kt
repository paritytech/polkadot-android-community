package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.compaction.CompactionExpansionWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.download.FileDownloadWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload.FileUploadWorker

class HopTransferWakeUpWorker(appContext: Context, parameters: WorkerParameters) : Worker(appContext, parameters) {
    override fun doWork(): Result {
        val transfer = HopTransfer.entries.find { it.name == inputData.getString(KEY_TRANSFER) } ?: return Result.failure()

        when (transfer) {
            HopTransfer.UPLOAD -> FileUploadWorker.startUploadingWork(applicationContext)
            HopTransfer.DOWNLOAD -> FileDownloadWorker.startDownloadingWork(applicationContext)
            HopTransfer.COMPACTION_EXPANSION -> CompactionExpansionWorker.startExpansionWork(applicationContext)
        }

        return Result.success()
    }

    companion object {
        private const val KEY_TRANSFER = "transfer"

        fun inputFor(transfer: HopTransfer): Data = workDataOf(KEY_TRANSFER to transfer.name)

        fun workIdFor(transfer: HopTransfer): String = "HopTransferWakeUp${transfer.name}"
    }
}
