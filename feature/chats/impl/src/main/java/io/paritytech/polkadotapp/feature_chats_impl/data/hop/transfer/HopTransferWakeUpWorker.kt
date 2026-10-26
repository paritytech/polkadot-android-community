package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.compaction.CompactionExpansionStarter
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.download.FileDownloadStarter
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload.FileUploadStarter

@HiltWorker
class HopTransferWakeUpWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val uploadStarter: FileUploadStarter,
    private val downloadStarter: FileDownloadStarter,
    private val compactionExpansionStarter: CompactionExpansionStarter
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val transfer = HopTransfer.entries.find { it.name == inputData.getString(KEY_TRANSFER) } ?: return Result.failure()

        when (transfer) {
            HopTransfer.UPLOAD -> uploadStarter.startUploading()
            HopTransfer.DOWNLOAD -> downloadStarter.startDownloading()
            HopTransfer.COMPACTION_EXPANSION -> compactionExpansionStarter.startExpansion()
        }

        return Result.success()
    }

    companion object {
        private const val KEY_TRANSFER = "transfer"

        fun inputFor(transfer: HopTransfer): Data = workDataOf(KEY_TRANSFER to transfer.name)
    }
}
