package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

enum class HopTransfer(val wakeUpWorkId: String) {
    UPLOAD("HopFileUploadWakeUp"),
    DOWNLOAD("HopFileDownloadWakeUp"),
    COMPACTION_EXPANSION("HopCompactionExpansionWakeUp")
}
