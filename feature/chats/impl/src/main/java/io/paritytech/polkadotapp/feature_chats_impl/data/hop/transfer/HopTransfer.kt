package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

enum class HopTransfer(val workId: String) {
    UPLOAD("HopFileUpload"),
    DOWNLOAD("HopFileDownload"),
    COMPACTION_EXPANSION("HopCompactionExpansion");

    val wakeUpWorkId: String get() = "${workId}WakeUp"
}
