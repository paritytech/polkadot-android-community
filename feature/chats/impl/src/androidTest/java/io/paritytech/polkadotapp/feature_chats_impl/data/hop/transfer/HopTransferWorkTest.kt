package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.compaction.CompactionExpansionWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.download.FileDownloadWorker
import io.paritytech.polkadotapp.feature_chats_impl.data.hop.upload.FileUploadWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class HopTransferWorkTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver

    @Before
    fun setUp() {
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)

        workManager = WorkManager.getInstance(context)
        testDriver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
    }

    @Test
    fun aNewUploadIsQueuedWithoutDelayWhileABackoffWakeUpIsPending() {
        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.hours)

        FileUploadWorker.startUploadingWork(context)

        assertEquals(0L, workTaggedWith<FileUploadWorker>().single().initialDelayMillis)
    }

    @Test
    fun theBackoffWakeUpStaysScheduledWhenANewUploadIsQueued() {
        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.hours)

        FileUploadWorker.startUploadingWork(context)

        assertEquals(listOf(WorkInfo.State.ENQUEUED), wakeUpStates(HopTransfer.UPLOAD))
    }

    @Test
    fun anUploadPassIsQueuedWhenTheBackoffWakeUpFires() {
        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.hours)

        testDriver.setInitialDelayMet(pendingWakeUp(HopTransfer.UPLOAD).id)

        assertEquals(listOf(WorkInfo.State.ENQUEUED), workTaggedWith<FileUploadWorker>().map { it.state })
    }

    @Test
    fun aDownloadPassIsQueuedWhenTheBackoffWakeUpFires() {
        scheduleTransferWakeUp(context, HopTransfer.DOWNLOAD, Clock.System.now() + 1.hours)

        testDriver.setInitialDelayMet(pendingWakeUp(HopTransfer.DOWNLOAD).id)

        assertEquals(listOf(WorkInfo.State.ENQUEUED), workTaggedWith<FileDownloadWorker>().map { it.state })
    }

    @Test
    fun anExpansionPassIsQueuedWhenTheBackoffWakeUpFires() {
        scheduleTransferWakeUp(context, HopTransfer.COMPACTION_EXPANSION, Clock.System.now() + 1.hours)

        testDriver.setInitialDelayMet(pendingWakeUp(HopTransfer.COMPACTION_EXPANSION).id)

        assertEquals(listOf(WorkInfo.State.ENQUEUED), workTaggedWith<CompactionExpansionWorker>().map { it.state })
    }

    @Test
    fun theWakeUpTakesTheLatestDeadlineWhenItIsScheduledAgain() {
        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.minutes)

        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.hours)

        assertTrue(pendingWakeUp(HopTransfer.UPLOAD).initialDelayMillis > 1.minutes.inWholeMilliseconds)
    }

    @Test
    fun anUploadWakeUpStaysScheduledWhenADownloadWakeUpIsScheduled() {
        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.minutes)

        scheduleTransferWakeUp(context, HopTransfer.DOWNLOAD, Clock.System.now() + 1.hours)

        assertEquals(2, workTaggedWith<HopTransferWakeUpWorker>().count { it.state == WorkInfo.State.ENQUEUED })
    }

    private inline fun <reified W : ListenableWorker> workTaggedWith(): List<WorkInfo> = workManager.getWorkInfosByTag(W::class.java.name).get()

    private fun pendingWakeUp(transfer: HopTransfer): WorkInfo =
        workManager.getWorkInfosForUniqueWork(transfer.wakeUpWorkId).get().single { it.state == WorkInfo.State.ENQUEUED }

    private fun wakeUpStates(transfer: HopTransfer): List<WorkInfo.State> =
        workManager.getWorkInfosForUniqueWork(transfer.wakeUpWorkId).get().map { it.state }
}
