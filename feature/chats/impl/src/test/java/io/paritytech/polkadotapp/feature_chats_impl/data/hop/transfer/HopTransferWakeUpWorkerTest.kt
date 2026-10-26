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
        withUploadPassQueued()

        wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.UPLOAD)).doWork()

        verifyUploadPassRequested()
    }

    @Test
    fun `the wake-up asks for a download pass when it was scheduled for downloads`() = runBlocking<Unit> {
        withDownloadPassQueued()

        wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.DOWNLOAD)).doWork()

        verifyDownloadPassRequested()
    }

    @Test
    fun `the wake-up asks for an expansion pass when it was scheduled for compaction expansion`() = runBlocking<Unit> {
        withExpansionPassQueued()

        wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.COMPACTION_EXPANSION)).doWork()

        verifyExpansionPassRequested()
    }

    @Test
    fun `the wake-up asks for no other pass when it was scheduled for uploads`() = runBlocking<Unit> {
        withUploadPassQueued()

        wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.UPLOAD)).doWork()

        verifyNoDownloadOrExpansionPass()
    }

    @Test
    fun `the wake-up succeeds when its pass is queued`() = runBlocking<Unit> {
        withUploadPassQueued()

        val result = wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.UPLOAD)).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `the wake-up retries when its pass could not be queued`() = runBlocking<Unit> {
        withUploadPassFailing()

        val result = wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.UPLOAD)).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `the wake-up fails when its pass still could not be queued on its last retry`() = runBlocking<Unit> {
        withUploadPassFailing()

        val result = wakeUpWith(HopTransferWakeUpWorker.inputFor(HopTransfer.UPLOAD), runAttemptCount = HopTransferWakeUpWorker.MAX_RETRIES).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `the wake-up fails when it names no transfer`() = runBlocking<Unit> {
        val result = wakeUpWith(Data.EMPTY).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    private suspend fun withUploadPassQueued() {
        whenever(uploadStarter.startUploading()).thenReturn(Result.success(Unit))
    }

    private suspend fun withUploadPassFailing() {
        whenever(uploadStarter.startUploading()).thenReturn(Result.failure(IllegalStateException("work database unavailable")))
    }

    private suspend fun withDownloadPassQueued() {
        whenever(downloadStarter.startDownloading()).thenReturn(Result.success(Unit))
    }

    private suspend fun withExpansionPassQueued() {
        whenever(compactionExpansionStarter.startExpansion()).thenReturn(Result.success(Unit))
    }

    private fun wakeUpWith(input: Data, runAttemptCount: Int = 0): HopTransferWakeUpWorker {
        val parameters: WorkerParameters = mock(WorkerParameters::class.java)
        whenever(parameters.inputData).thenReturn(input)
        whenever(parameters.runAttemptCount).thenReturn(runAttemptCount)

        return HopTransferWakeUpWorker(
            appContext = mock(Context::class.java),
            parameters = parameters,
            uploadStarter = uploadStarter,
            downloadStarter = downloadStarter,
            compactionExpansionStarter = compactionExpansionStarter
        )
    }

    private suspend fun verifyUploadPassRequested() {
        verify(uploadStarter).startUploading()
    }

    private suspend fun verifyDownloadPassRequested() {
        verify(downloadStarter).startDownloading()
    }

    private suspend fun verifyExpansionPassRequested() {
        verify(compactionExpansionStarter).startExpansion()
    }

    private suspend fun verifyNoDownloadOrExpansionPass() {
        verify(downloadStarter, never()).startDownloading()
        verify(compactionExpansionStarter, never()).startExpansion()
    }
}
