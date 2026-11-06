package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileFetcher
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileOutcome
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileRequest
import javax.inject.Inject

class RealSiblingFileFetcher @Inject constructor(
    private val registry: SyncFileChannelRegistry,
) : SiblingFileFetcher {
    private companion object {
        // Keeps a filesRequest well below the 64 KB SCTP max-message-size.
        const val MAX_FILES_PER_REQUEST = 64
    }

    override suspend fun fetch(requests: List<SiblingFileRequest>): List<SiblingFileOutcome> {
        val requester = registry.openRequester()
            ?: return requests.map { SiblingFileOutcome.Unavailable(SiblingFileOutcome.Unavailable.Reason.NOT_STORED) }

        return requests.chunked(MAX_FILES_PER_REQUEST).flatMap { requester.request(it) }
    }
}
