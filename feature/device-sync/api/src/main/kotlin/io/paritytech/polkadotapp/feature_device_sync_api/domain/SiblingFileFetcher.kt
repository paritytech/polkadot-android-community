package io.paritytech.polkadotapp.feature_device_sync_api.domain

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import java.io.File

/**
 * Fetches files from an own device over its open device sync data channel (`device-sync-file` use case):
 * the fallback for referenced files that are not in on-chain storage.
 */
interface SiblingFileFetcher {
    /**
     * Returns one outcome per request, in the same order. Without an open channel to a sibling every
     * request is [SiblingFileOutcome.Unavailable] with [SiblingFileOutcome.Unavailable.Reason.NOT_STORED] right away.
     */
    suspend fun fetch(requests: List<SiblingFileRequest>): List<SiblingFileOutcome>
}

/**
 * [destination] receives the file once complete. The bytes received so far are kept next to it, so a fetch that
 * was interrupted resumes from where it stopped.
 */
class SiblingFileRequest(
    val file: SiblingFile,
    val destination: File,
)

sealed interface SiblingFile {
    /** Hosted attachment [identifier] (`P2PMixnetFile.identifier`) of chat message [messageId]. */
    data class MessageAttachment(val messageId: String, val identifier: DataByteArray) : SiblingFile
}

sealed interface SiblingFileOutcome {
    data object Completed : SiblingFileOutcome

    data class Unavailable(val reason: Reason) : SiblingFileOutcome {
        enum class Reason {
            NOT_STORED,

            /** The sibling is downloading the file itself; request it again later. */
            DOWNLOADING
        }
    }

    /** The received size didn't match the announced one; the partial file was discarded. */
    data object SizeMismatch : SiblingFileOutcome

    /** The request ended before the file finished; its received bytes are kept for a later resume. */
    data object Interrupted : SiblingFileOutcome
}
