package io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.HopTransferWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.enqueueExpeditedDrain
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.FileUploadRepository
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.FileUpload
import io.paritytech.polkadotapp.feature_chats_impl.domain.hop.toFileUploadError
import io.paritytech.polkadotapp.common.R as RCommon

@HiltWorker
class FileUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val fileUploadRepository: FileUploadRepository,
    private val hopFileUploader: HopFileUploader
) : HopTransferWorker<FileUpload>(appContext, parameters) {
    override val notificationId = NOTIFICATION_ID
    override val notificationTitleRes = RCommon.string.chat_file_upload_worker_notification_title
    override val notificationMessageRes = RCommon.string.chat_file_upload_worker_notification_message

    override suspend fun nextPending(): FileUpload? = fileUploadRepository.getNextPending()
    override suspend fun markInProgress(item: FileUpload) = fileUploadRepository.markInProgress(item.messageId)
    override suspend fun process(item: FileUpload) = hopFileUploader.upload(item)
    override suspend fun markDone(item: FileUpload) = fileUploadRepository.markDone(item.messageId)
    override suspend fun markFailed(item: FileUpload, error: Throwable) =
        fileUploadRepository.markFailed(item.messageId, error.toFileUploadError())

    companion object {
        private const val WORK_ID = "HopFileUpload"
        private const val NOTIFICATION_ID = 1002

        fun startUploadingWork(context: Context) = enqueueExpeditedDrain<FileUploadWorker>(context, WORK_ID)
    }
}
