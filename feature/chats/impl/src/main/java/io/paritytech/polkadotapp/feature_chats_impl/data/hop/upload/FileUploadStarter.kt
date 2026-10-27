package io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.HopTransferPassLocks
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer.enqueueExpeditedTransfer
import javax.inject.Inject

class FileUploadStarter @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val passLocks: HopTransferPassLocks
) {
    suspend fun startUploading(): Result<Unit> = enqueueExpeditedTransfer<FileUploadWorker>(appContext, FileUploadWorker.WORK_ID, passLocks)
}
