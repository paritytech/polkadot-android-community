package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed class SyncFileWriterException(message: String) : IOException(message) {
    class UnexpectedOffset(expected: InformationSize, received: InformationSize) :
        SyncFileWriterException("Expected a chunk at ${expected.inWholeBytes}, got ${received.inWholeBytes}")

    class SizeMismatch(expected: InformationSize, stored: InformationSize) :
        SyncFileWriterException("Expected ${expected.inWholeBytes} bytes, stored ${stored.inWholeBytes}")
}

/**
 * Appends a file received from a sibling to `<destination>.sync.part` and moves it to [destination] once
 * complete. The partial file is the resume state: its size is the offset of the next request.
 */
class SyncFileWriter(private val destination: File) {
    companion object {
        const val PARTIAL_SUFFIX = ".sync.part"
    }

    private val partial = File(destination.path + PARTIAL_SUFFIX)
    private var output: FileOutputStream? = null

    fun storedBytes(): InformationSize = partial.length().bytes

    fun write(data: ByteArray, offset: InformationSize) {
        val stored = storedBytes()
        if (offset != stored) throw SyncFileWriterException.UnexpectedOffset(expected = stored, received = offset)

        openOutput().write(data)
    }

    fun finish(totalSize: InformationSize) {
        output?.fd?.sync()
        close()

        if (destination.exists()) {
            discard()
            return
        }

        val stored = storedBytes()
        if (stored != totalSize) {
            discard()
            throw SyncFileWriterException.SizeMismatch(expected = totalSize, stored = stored)
        }

        if (!partial.exists()) {
            partial.parentFile?.mkdirs()
            partial.createNewFile()
        }
        if (!partial.renameTo(destination)) throw IOException("Failed to move ${partial.name} to ${destination.name}")
    }

    fun discard() {
        close()
        partial.delete()
    }

    fun close() {
        runCatching { output?.close() }
        output = null
    }

    private fun openOutput(): FileOutputStream {
        output?.let { return it }

        partial.parentFile?.mkdirs()
        val append = true
        return FileOutputStream(partial, append).also { output = it }
    }
}
