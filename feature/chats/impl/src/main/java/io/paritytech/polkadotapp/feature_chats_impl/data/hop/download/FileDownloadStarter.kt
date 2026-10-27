package io.paritytech.polkadotapp.feature_chats_impl.data.hop.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.HopTransferPassLocks
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.enqueueExpeditedTransfer
import javax.inject.Inject

class FileDownloadStarter @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val passLocks: HopTransferPassLocks
) {
    suspend fun startDownloading(): Result<Unit> = enqueueExpeditedTransfer<FileDownloadWorker>(appContext, FileDownloadWorker.WORK_ID, passLocks)
}
