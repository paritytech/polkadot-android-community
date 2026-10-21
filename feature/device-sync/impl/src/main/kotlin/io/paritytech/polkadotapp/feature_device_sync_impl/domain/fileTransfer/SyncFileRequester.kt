package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFile
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileOutcome
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileRequest
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileChunkScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileHeaderScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileReferenceScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileRequestItemScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileUnavailableReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileUnavailableScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FilesRequestScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.MessageAttachmentIdScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncFileMessageScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import kotlin.time.Duration

/** Requests files from the sibling on one data channel; request ids are unique for the channel's lifetime. */
class SyncFileRequester(
    private val sender: SyncFileSender,
    private val peerLogId: String,
    private val noResponseTimeout: Duration,
    private val scope: CoroutineScope,
) {
    private class InFlightFile(val writer: SyncFileWriter) {
        var totalSize: InformationSize? = null
        var outcome: SiblingFileOutcome? = null
    }

    private class InFlightRequest(
        val files: List<InFlightFile>,
        val result: CompletableDeferred<List<SiblingFileOutcome>>,
    ) {
        var hasResponse = false
        var timeoutJob: Job? = null

        fun file(index: UInt): InFlightFile? = files.getOrNull(index.toInt())
    }

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<UInt, InFlightRequest>()
    private var nextRequestId = 0u
    private var providerMessages = 0L
    private var closed = false

    /** Returns one outcome per request, in the same order. */
    suspend fun request(requests: List<SiblingFileRequest>): List<SiblingFileOutcome> {
        if (requests.isEmpty()) return emptyList()

        val result = CompletableDeferred<List<SiblingFileOutcome>>()
        val wireRequest = mutex.withLock {
            if (closed) return requests.map { SiblingFileOutcome.Interrupted }
            startRequest(requests, result)
        }
        // Sent from the channel's scope: a send on a channel that has just closed never returns.
        scope.launch { sendRequest(wireRequest) }

        return try {
            result.await()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { abandon(wireRequest.requestId) }
            throw e
        }
    }

    suspend fun handle(message: SyncFileMessageScale) {
        mutex.withLock {
            providerMessages++

            when (message) {
                is SyncFileMessageScale.Header -> handleHeader(message.header)
                is SyncFileMessageScale.Chunk -> handleChunk(message.chunk)
                is SyncFileMessageScale.Unavailable -> handleUnavailable(message.unavailable)
                is SyncFileMessageScale.Completed -> finishRequest(message.requestId, remaining = SiblingFileOutcome.Interrupted)

                is SyncFileMessageScale.FilesRequest,
                is SyncFileMessageScale.CancelFilesRequest,
                is SyncFileMessageScale.AvailabilityRequest,
                is SyncFileMessageScale.AvailabilityResponse -> Unit
            }
        }
    }

    suspend fun close() {
        mutex.withLock {
            closed = true
            inFlight.keys.toList().forEach { finishRequest(it, remaining = SiblingFileOutcome.Interrupted) }
        }
    }

    private fun startRequest(
        requests: List<SiblingFileRequest>,
        result: CompletableDeferred<List<SiblingFileOutcome>>,
    ): FilesRequestScale {
        val requestId = nextRequestId++
        val files = requests.map { InFlightFile(SyncFileWriter(it.destination)) }

        inFlight[requestId] = InFlightRequest(files, result).apply {
            timeoutJob = scope.launch { awaitResponse(requestId) }
        }

        return FilesRequestScale(
            requestId = requestId,
            files = requests.zip(files) { request, file ->
                FileRequestItemScale(request.file.toScale(), file.writer.storedBytes().inWholeBytes.toULong())
            }
        )
    }

    private suspend fun sendRequest(request: FilesRequestScale) {
        sender.sendCatching(SyncFileMessageScale.FilesRequest(request))
            .onFailure { mutex.withLock { finishRequest(request.requestId, remaining = SiblingFileOutcome.Interrupted) } }
            .logFailure("SyncFileRequester: failed to send request ${request.requestId} to $peerLogId")
    }

    // The provider serves requests one at a time, so only its full silence means it can't serve this one.
    private suspend fun awaitResponse(requestId: UInt) {
        while (true) {
            val messagesBefore = mutex.withLock { providerMessages }
            delay(noResponseTimeout)

            mutex.withLock {
                val request = inFlight[requestId] ?: return
                if (request.hasResponse) return

                if (providerMessages == messagesBefore) {
                    Timber.w("SyncFileRequester: request $requestId to $peerLogId got no response, treating files as not stored")
                    finishRequest(requestId, remaining = SiblingFileOutcome.Unavailable(SiblingFileOutcome.Unavailable.Reason.NOT_STORED))
                    return
                }
            }
        }
    }

    private suspend fun abandon(requestId: UInt) {
        val wasInFlight = mutex.withLock { finishRequest(requestId, remaining = SiblingFileOutcome.Interrupted) }

        if (wasInFlight) {
            scope.launch {
                sender.sendCatching(SyncFileMessageScale.CancelFilesRequest(requestId))
                    .logFailure("SyncFileRequester: failed to cancel request $requestId to $peerLogId")
            }
        }
    }

    private fun handleHeader(header: FileHeaderScale) {
        val request = inFlight[header.requestId] ?: return
        val file = request.file(header.fileIndex)?.takeIf { it.outcome == null } ?: return

        markResponse(request)
        val totalSize = header.totalSize.toLong().bytes
        file.totalSize = totalSize

        if (file.writer.storedBytes() >= totalSize) completeFile(request, file, totalSize)
    }

    private fun handleChunk(chunk: FileChunkScale) {
        val request = inFlight[chunk.requestId] ?: return
        val file = request.file(chunk.fileIndex)?.takeIf { it.outcome == null } ?: return
        val totalSize = file.totalSize ?: return

        try {
            file.writer.write(chunk.data, offset = chunk.offset.toLong().bytes)
        } catch (e: IOException) {
            Timber.e(e, "SyncFileRequester: rejected chunk of request ${chunk.requestId} file ${chunk.fileIndex}")
            file.writer.discard()
            resolveFile(request, file, SiblingFileOutcome.SizeMismatch)
            return
        }

        if (file.writer.storedBytes() >= totalSize) completeFile(request, file, totalSize)
    }

    private fun handleUnavailable(unavailable: FileUnavailableScale) {
        val request = inFlight[unavailable.requestId] ?: return
        val file = request.file(unavailable.fileIndex) ?: return

        resolveFile(request, file, SiblingFileOutcome.Unavailable(unavailable.reason.toOutcomeReason()))
    }

    private fun completeFile(request: InFlightRequest, file: InFlightFile, totalSize: InformationSize) {
        try {
            file.writer.finish(totalSize)
            resolveFile(request, file, SiblingFileOutcome.Completed)
        } catch (e: IOException) {
            Timber.e(e, "SyncFileRequester: failed to finish a file from $peerLogId")
            resolveFile(request, file, SiblingFileOutcome.SizeMismatch)
        }
    }

    private fun resolveFile(request: InFlightRequest, file: InFlightFile, outcome: SiblingFileOutcome) {
        if (file.outcome != null) return

        markResponse(request)
        file.writer.close()
        file.outcome = outcome
    }

    private fun markResponse(request: InFlightRequest) {
        if (request.hasResponse) return

        request.hasResponse = true
        request.timeoutJob?.cancel()
    }

    private fun finishRequest(requestId: UInt, remaining: SiblingFileOutcome): Boolean {
        val request = inFlight.remove(requestId) ?: return false

        request.timeoutJob?.cancel()
        val outcomes = request.files.map { file ->
            file.writer.close()
            file.outcome ?: remaining
        }
        request.result.complete(outcomes)

        return true
    }

    private fun SiblingFile.toScale(): FileReferenceScale = when (this) {
        is SiblingFile.MessageAttachment -> FileReferenceScale.MessageAttachment(
            MessageAttachmentIdScale(messageId = messageId, identifier = identifier.value)
        )
    }

    private fun FileUnavailableReasonScale.toOutcomeReason(): SiblingFileOutcome.Unavailable.Reason = when (this) {
        FileUnavailableReasonScale.NOT_STORED -> SiblingFileOutcome.Unavailable.Reason.NOT_STORED
        FileUnavailableReasonScale.DOWNLOADING -> SiblingFileOutcome.Unavailable.Reason.DOWNLOADING
    }
}
