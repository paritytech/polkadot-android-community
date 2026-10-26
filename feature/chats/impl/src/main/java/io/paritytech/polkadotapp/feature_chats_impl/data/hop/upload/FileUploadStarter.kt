package io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class FileUploadStarter @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) {
    suspend fun startUploading(): Result<Unit> = FileUploadWorker.startUploadingWork(appContext)
}
