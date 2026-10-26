package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.compaction.CompactionExpansionStarter
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.download.FileDownloadStarter
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload.FileUploadStarter
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class HopTransferWakeUpWorkerTest {
    private val uploadStarter: FileUploadStarter = mock(FileUploadStarter::class.java)
    private val downloadStarter: FileDownloadStarter = mock(FileDownloadStarter::class.java)
    private val compactionExpansionStarter: CompactionExpansionStarter = mock(CompactionExpansionStarter::class.java)

    @Test
    fun `the wake-up asks for an upload pass when it was scheduled for uploads`() = runBlocking<Unit> {
        wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.UPLOAD)).doWork()

        verify(uploadStarter).startUploading()
    }

    @Test
    fun `the wake-up asks for a download pass when it was scheduled for downloads`() = runBlocking<Unit> {
        wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.DOWNLOAD)).doWork()

        verify(downloadStarter).startDownloading()
    }

    @Test
    fun `the wake-up asks for an expansion pass when it was scheduled for compaction expansion`() = runBlocking<Unit> {
        wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.COMPACTION_EXPANSION)).doWork()

        verify(compactionExpansionStarter).startExpansion()
    }

    @Test
    fun `the wake-up asks for no other pass when it was scheduled for uploads`() = runBlocking<Unit> {
        wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.UPLOAD)).doWork()

        verifyNoDownloadOrExpansionPass()
    }

    @Test
    fun `the wake-up fails when it names no transfer`() = runBlocking<Unit> {
        val result = wakeUpWith(Data.EMPTY).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    private fun wakeUpWith(input: Data): HopTransferWakeUpWorker {
        val parameters: WorkerParameters = mock(WorkerParameters::class.java)
        whenever(parameters.inputData).thenReturn(input)

        return HopTransferWakeUpWorker(
            appContext = mock(Context::class.java),
            parameters = parameters,
            uploadStarter = uploadStarter,
            downloadStarter = downloadStarter,
            compactionExpansionStarter = compactionExpansionStarter
        )
    }

    private suspend fun verifyNoDownloadOrExpansionPass() {
        verify(downloadStarter, never()).startDownloading()
        verify(compactionExpansionStarter, never()).startExpansion()
    }
}
