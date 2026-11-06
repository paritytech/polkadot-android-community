package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.kilobytes
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileAvailabilityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileChunkScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileHeaderScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileRequestItemScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileUnavailableReasonScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FileUnavailableScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FilesAvailabilityRequestScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FilesAvailabilityResponseScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.FilesRequestScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncFileMessageScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.InputStream

/**
 * Serves `device-sync-file` requests in arrival order, one at a time, one file at a time.
 * Every request ends with `completed`, including a cancelled one.
 */
class SyncFileProvider(
    private val sender: SyncFileSender,
    private val locator: SyncFileLocator,
    private val peerLogId: String,
    private val scope: CoroutineScope,
) {
    private companion object {
        // With the chunk and DataChannelMessage framing it stays below the 64 KB SCTP max-message-size.
        val CHUNK_PAYLOAD_SIZE = 60.kilobytes
    }

    private val mutex = Mutex()
    private val pendingRequests = ArrayDeque<FilesRequestScale>()
    private val cancelledRequestIds = mutableSetOf<UInt>()
    private var currentRequestId: UInt? = null
    private var servingJob: Job? = null

    suspend fun handle(request: FilesRequestScale) {
        mutex.withLock {
            pendingRequests.addLast(request)
            if (servingJob == null) servingJob = scope.launch { servePendingRequests() }
        }
    }

    suspend fun handle(request: FilesAvailabilityRequestScale) {
        val availability = request.files.map { file ->
            when (val location = locator.locate(file)) {
                is SyncFileLocation.Stored -> FileAvailabilityScale.Available(location.size.toWire())
                is SyncFileLocation.Unavailable -> FileAvailabilityScale.Unavailable(location.reason)
            }
        }
        val response = FilesAvailabilityResponseScale(request.requestId, availability)

        sender.sendCatching(SyncFileMessageScale.AvailabilityResponse(response))
            .logFailure("SyncFileProvider: failed to answer availability request ${request.requestId} from $peerLogId")
    }

    suspend fun cancel(requestId: UInt) {
        val droppedPending = mutex.withLock {
            if (currentRequestId == requestId) {
                cancelledRequestIds.add(requestId)
                false
            } else {
                pendingRequests.removeAll { it.requestId == requestId }
            }
        }

        if (droppedPending) {
            sender.sendCatching(SyncFileMessageScale.Completed(requestId))
                .logFailure("SyncFileProvider: failed to complete cancelled request $requestId from $peerLogId")
        }
    }

    private suspend fun servePendingRequests() {
        while (true) {
            val request = mutex.withLock { takeNextRequest() } ?: return

            runCancellableCatching { serve(request) }
                .onFailure { mutex.withLock { pendingRequests.clear() } }
                .logFailure("SyncFileProvider: request ${request.requestId} from $peerLogId aborted")

            mutex.withLock {
                currentRequestId = null
                cancelledRequestIds.remove(request.requestId)
            }
        }
    }

    private fun takeNextRequest(): FilesRequestScale? {
        val next = pendingRequests.removeFirstOrNull()
        currentRequestId = next?.requestId
        if (next == null) servingJob = null
        return next
    }

    private suspend fun serve(request: FilesRequestScale) {
        Timber.i("SyncFileProvider: serving request ${request.requestId} with ${request.files.size} files to $peerLogId")

        for ((index, item) in request.files.withIndex()) {
            if (isCancelled(request.requestId)) break
            serve(item, fileIndex = index.toUInt(), requestId = request.requestId)
        }

        sender.send(SyncFileMessageScale.Completed(request.requestId))
    }

    private suspend fun serve(item: FileRequestItemScale, fileIndex: UInt, requestId: UInt) {
        when (val location = locator.locate(item.file)) {
            is SyncFileLocation.Unavailable -> sendUnavailable(location.reason, fileIndex, requestId)

            is SyncFileLocation.Stored -> {
                sender.send(SyncFileMessageScale.Header(FileHeaderScale(requestId, fileIndex, location.size.toWire())))

                val isComplete = streamChunks(location, item.offset.toLong().bytes, fileIndex, requestId)
                if (!isComplete && !isCancelled(requestId)) {
                    sendUnavailable(FileUnavailableReasonScale.NOT_STORED, fileIndex, requestId)
                }
            }
        }
    }

    // False when the file can't be read up to its announced size: deleted or truncated meanwhile.
    private suspend fun streamChunks(
        file: SyncFileLocation.Stored,
        from: InformationSize,
        fileIndex: UInt,
        requestId: UInt,
    ): Boolean {
        if (from >= file.size) return true

        val input = runCatching { file.open() }.getOrNull() ?: return false

        return input.use { stream ->
            if (!runCatching { stream.skipExactly(from.inWholeBytes) }.getOrDefault(false)) return@use false

            val buffer = ByteArray(CHUNK_PAYLOAD_SIZE.inWholeBytes.toInt())
            var offset = from

            while (offset < file.size) {
                if (isCancelled(requestId)) return@use false

                val length = minOf(buffer.size.toLong(), (file.size - offset).inWholeBytes).toInt()
                val read = runCatching { stream.readUpTo(buffer, length) }.getOrDefault(0)
                if (read == 0) return@use false

                val chunk = FileChunkScale(requestId, fileIndex, offset.toWire(), buffer.copyOf(read))
                sender.send(SyncFileMessageScale.Chunk(chunk))
                offset += read.bytes
            }

            true
        }
    }

    private suspend fun sendUnavailable(reason: FileUnavailableReasonScale, fileIndex: UInt, requestId: UInt) {
        sender.send(SyncFileMessageScale.Unavailable(FileUnavailableScale(requestId, fileIndex, reason)))
    }

    private suspend fun isCancelled(requestId: UInt): Boolean = mutex.withLock { requestId in cancelledRequestIds }

    private fun InformationSize.toWire(): ULong = inWholeBytes.toULong()

    private fun InputStream.skipExactly(count: Long): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (read() < 0) return false
                remaining--
            }
        }
        return true
    }

    private fun InputStream.readUpTo(buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = read(buffer, total, length - total)
            if (read < 0) break
            total += read
        }
        return total
    }
}
