package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit
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
    fun aTransferGetsAPassWhileABackoffWakeUpIsPending() = runBlocking<Unit> {
        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.hours)

        enqueueQuietTransfer<PassWorker>(context, HopTransfer.UPLOAD.workId)
        runQueuedWork(HopTransfer.UPLOAD.workId)

        assertEquals(1, finishedPasses(HopTransfer.UPLOAD.workId))
    }

    @Test
    fun theBackoffWakeUpStaysScheduledWhenATransferGetsAPass() = runBlocking<Unit> {
        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.hours)

        enqueueQuietTransfer<PassWorker>(context, HopTransfer.UPLOAD.workId)
        runQueuedWork(HopTransfer.UPLOAD.workId)

        assertEquals(listOf(WorkInfo.State.ENQUEUED), statesOf(HopTransferWakeUpWorker.workIdFor(HopTransfer.UPLOAD)))
    }

    @Test
    fun aPassIsNotQueuedWhenOneIsAlreadyWaitingToStart() = runBlocking<Unit> {
        enqueueQuietTransfer<PassWorker>(context, PASS_WORK_ID)
        enqueueQuietTransfer<PassWorker>(context, PASS_WORK_ID)

        runQueuedWork(PASS_WORK_ID)

        assertEquals(1, finishedPasses(PASS_WORK_ID))
    }

    @Test
    fun theWakeUpIsCancelledWhenNothingIsWaitingOutABackoff() = runBlocking<Unit> {
        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.hours)

        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, executeAt = null)

        assertEquals(listOf(WorkInfo.State.CANCELLED), statesOf(HopTransferWakeUpWorker.workIdFor(HopTransfer.UPLOAD)))
    }

    @Test
    fun aTransferGetsAPassWhenTheWaitingPassIsDelayed() = runBlocking<Unit> {
        enqueueDelayedPass(PASS_WORK_ID)

        enqueueQuietTransfer<PassWorker>(context, PASS_WORK_ID)
        runQueuedWork(PASS_WORK_ID)

        assertEquals(1, finishedPasses(PASS_WORK_ID))
    }

    @Test
    fun theWakeUpMovesToTheSoonerDeadlineWhenItIsScheduledAgain() = runBlocking<Unit> {
        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.hours)

        scheduleTransferWakeUp(context, HopTransfer.UPLOAD, Clock.System.now() + 1.minutes)

        assertTrue(pendingWakeUp(HopTransfer.UPLOAD).initialDelayMillis <= 1.minutes.inWholeMilliseconds)
    }

    private fun enqueueDelayedPass(workId: String) {
        val request = OneTimeWorkRequestBuilder<PassWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniqueWork(workId, ExistingWorkPolicy.REPLACE, request).result.get()
    }

    private fun pendingWakeUp(transfer: HopTransfer): WorkInfo =
        workManager.getWorkInfosForUniqueWork(HopTransferWakeUpWorker.workIdFor(transfer)).get().single { it.state == WorkInfo.State.ENQUEUED }

    private fun runQueuedWork(workId: String) {
        val started = mutableSetOf<UUID>()

        while (true) {
            val next = workManager.getWorkInfosForUniqueWork(workId).get()
                .firstOrNull { it.state == WorkInfo.State.ENQUEUED && it.id !in started }
                ?: break

            started += next.id
            testDriver.setAllConstraintsMet(next.id)
        }
    }

    private fun finishedPasses(workId: String): Int = statesOf(workId).count { it == WorkInfo.State.SUCCEEDED }

    private fun statesOf(workId: String): List<WorkInfo.State> = workManager.getWorkInfosForUniqueWork(workId).get().map { it.state }

    class PassWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
        override fun doWork(): Result = Result.success()
    }

    private companion object {
        const val PASS_WORK_ID = "TestTransferPass"
    }
}
