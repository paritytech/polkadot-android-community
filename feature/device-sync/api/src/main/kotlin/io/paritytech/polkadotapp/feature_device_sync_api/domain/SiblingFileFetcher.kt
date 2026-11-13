package io.paritytech.polkadotapp.feature_device_sync_api.domain

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import java.io.File

interface SiblingFileFetcher {
    suspend fun fetch(requests: List<SiblingFileRequest>): List<SiblingFileOutcome>
}

class SiblingFileRequest(
    val file: SiblingFile,
    val destination: File,
)

sealed interface SiblingFile {
    data class MessageAttachment(val messageId: String, val identifier: DataByteArray) : SiblingFile
}

sealed interface SiblingFileOutcome {
    data object Completed : SiblingFileOutcome

    data class Unavailable(val reason: Reason) : SiblingFileOutcome {
        enum class Reason {
            NOT_STORED,
            DOWNLOADING
        }
    }

    data object SizeMismatch : SiblingFileOutcome

    data object Interrupted : SiblingFileOutcome
}
