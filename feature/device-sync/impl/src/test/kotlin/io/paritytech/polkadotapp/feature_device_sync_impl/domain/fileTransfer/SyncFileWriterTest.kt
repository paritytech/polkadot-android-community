package io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer

import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncFileWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val destination by lazy { File(folder.root, "downloads/file.bin") }
    private val partial by lazy { File(destination.path + SyncFileWriter.PARTIAL_SUFFIX) }

    @Test
    fun `chunks are appended and the finished file is moved into place`() {
        val writer = SyncFileWriter(destination)

        writer.write(byteArrayOf(1, 2), offset = 0.bytes)
        writer.write(byteArrayOf(3), offset = 2.bytes)
        writer.finish(totalSize = 3.bytes)

        assertArrayEquals(byteArrayOf(1, 2, 3), destination.readBytes())
        assertFalse(partial.exists())
    }

    @Test
    fun `chunk at an unexpected offset is rejected`() {
        val writer = SyncFileWriter(destination)
        writer.write(byteArrayOf(1, 2), offset = 0.bytes)

        assertThrows(SyncFileWriterException.UnexpectedOffset::class.java) {
            writer.write(byteArrayOf(3), offset = 3.bytes)
        }
        assertEquals(2.bytes, writer.storedBytes())
    }

    @Test
    fun `size mismatch discards the partial file`() {
        val writer = SyncFileWriter(destination)
        writer.write(byteArrayOf(1, 2), offset = 0.bytes)

        assertThrows(SyncFileWriterException.SizeMismatch::class.java) {
            writer.finish(totalSize = 3.bytes)
        }
        assertFalse(partial.exists())
        assertFalse(destination.exists())
    }

    @Test
    fun `stored bytes survive for a later writer`() {
        SyncFileWriter(destination).apply {
            write(byteArrayOf(1, 2), offset = 0.bytes)
            close()
        }

        val resumed = SyncFileWriter(destination)
        resumed.write(byteArrayOf(3), offset = 2.bytes)
        resumed.finish(totalSize = 3.bytes)

        assertArrayEquals(byteArrayOf(1, 2, 3), destination.readBytes())
    }

    @Test
    fun `empty file is created on finish`() {
        SyncFileWriter(destination).finish(totalSize = 0.bytes)

        assertTrue(destination.exists())
        assertEquals(0L, destination.length())
    }
}
