package io.paritytech.polkadotapp.feature_chats_impl.data.hop.compaction

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.common.utils.toWorkerResult
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.HopTransfer
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.HopTransferPassLocks
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.TransferQueueProcessor
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.scheduleTransferWakeUp

@HiltWorker
class CompactionExpansionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val transferQueueProcessor: TransferQueueProcessor,
    private val transferQueue: CompactionExpansionTransferQueue,
    private val passLocks: HopTransferPassLocks
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val processed = passLocks.runPass(HopTransfer.COMPACTION_EXPANSION) {
            runCancellableCatching { transferQueueProcessor.processPending(transferQueue) }
        }

        scheduleTransferWakeUp(applicationContext, HopTransfer.COMPACTION_EXPANSION, transferQueue.soonestNextAttemptAt())

        return processed.toWorkerResult(retryOnFailure = true)
    }

    companion object {
        const val WORK_ID = "HopCompactionExpansion"
    }
}
