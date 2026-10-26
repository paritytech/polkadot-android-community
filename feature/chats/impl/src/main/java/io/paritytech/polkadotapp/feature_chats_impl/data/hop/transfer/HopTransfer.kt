package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import io.paritytech.polkadotapp.feature_chats_impl.data.hop.compaction.CompactionExpansionWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.download.FileDownloadWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload.FileUploadWorker

enum class HopTransfer(val workId: String) {
    UPLOAD(FileUploadWorker.WORK_ID),
    DOWNLOAD(FileDownloadWorker.WORK_ID),
    COMPACTION_EXPANSION(CompactionExpansionWorker.WORK_ID)
}
