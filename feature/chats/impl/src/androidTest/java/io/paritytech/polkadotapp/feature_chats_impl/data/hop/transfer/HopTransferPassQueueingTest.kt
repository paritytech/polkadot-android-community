package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HopTransferPassQueueingTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val passLocks = HopTransferPassLocks()

    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver

    @Before
    fun setUp() {
        HoldingPassWorker.release = CountDownLatch(1)

        val configuration = Configuration.Builder()
            .setExecutor(Executors.newSingleThreadExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)

        workManager = WorkManager.getInstance(context)
        testDriver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
    }

    @After
    fun tearDown() {
        HoldingPassWorker.release.countDown()
    }

    @Test
    fun aTransferQueuedDuringARunningPassGetsOneMorePass() = runBlocking<Unit> {
        startHeldPass()

        enqueueQuietTransfer<HoldingPassWorker>(context, PASS_WORK_ID, passLocks)

        assertEquals(1, passesQueuedBehindTheRunningOne())
    }

    @Test
    fun aBurstOfTransfersDuringARunningPassQueuesOnePass() = runBlocking<Unit> {
        startHeldPass()

        List(BURST) { async(Dispatchers.IO) { enqueueQuietTransfer<HoldingPassWorker>(context, PASS_WORK_ID, passLocks) } }.awaitAll()

        assertEquals(1, passesQueuedBehindTheRunningOne())
    }

    @Test
    fun thePassQueuedDuringARunningPassRunsWhenThatPassFinishes() = runBlocking<Unit> {
        val held = startHeldPass()
        enqueueQuietTransfer<HoldingPassWorker>(context, PASS_WORK_ID, passLocks)

        HoldingPassWorker.release.countDown()
        awaitState(held, WorkInfo.State.SUCCEEDED)
        val next = startQueuedPass()

        awaitState(next, WorkInfo.State.SUCCEEDED)
    }

    @Test
    fun anExpeditedTransferQueuedDuringARunningPassGetsOneMorePass() = runBlocking<Unit> {
        startHeldPass()

        enqueueExpeditedTransfer<HoldingPassWorker>(context, PASS_WORK_ID, passLocks)

        assertEquals(1, passesQueuedBehindTheRunningOne())
    }

    @Test
    fun aTransferGetsAPassThatRunsWhenTheEarlierPassFailed() = runBlocking<Unit> {
        enqueueQuietTransfer<FailingPassWorker>(context, PASS_WORK_ID, passLocks)
        awaitState(startQueuedPass(), WorkInfo.State.FAILED)

        enqueueQuietTransfer<HoldingPassWorker>(context, PASS_WORK_ID, passLocks)
        HoldingPassWorker.release.countDown()
        val next = startQueuedPass()

        awaitState(next, WorkInfo.State.SUCCEEDED)
    }

    @Test
    fun aTransferGetsAPassWhenItsCallerIsCancelledBeforeTheEnqueue() = runBlocking<Unit> {
        startHeldPass()

        launch {
            coroutineContext.job.cancel()
            enqueueQuietTransfer<HoldingPassWorker>(context, PASS_WORK_ID, passLocks)
        }.join()

        assertEquals(1, passesQueuedBehindTheRunningOne())
    }

    private suspend fun startHeldPass(): UUID {
        enqueueQuietTransfer<HoldingPassWorker>(context, PASS_WORK_ID, passLocks)
        val pass = startQueuedPass()
        awaitState(pass, WorkInfo.State.RUNNING)
        return pass
    }

    private fun startQueuedPass(): UUID {
        val pass = workManager.getWorkInfosForUniqueWork(PASS_WORK_ID).get().single { it.state == WorkInfo.State.ENQUEUED }.id
        testDriver.setAllConstraintsMet(pass)
        return pass
    }

    private fun awaitState(id: UUID, state: WorkInfo.State) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (workManager.getWorkInfoById(id).get()?.state != state) {
            check(System.currentTimeMillis() < deadline) { "work $id never reached $state" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun passesQueuedBehindTheRunningOne(): Int =
        workManager.getWorkInfosForUniqueWork(PASS_WORK_ID).get().count { it.state == WorkInfo.State.BLOCKED }

    class HoldingPassWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
        override fun doWork(): Result {
            release.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            return Result.success()
        }

        companion object {
            @Volatile
            lateinit var release: CountDownLatch
        }
    }

    class FailingPassWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
        override fun doWork(): Result = Result.failure()
    }

    private companion object {
        const val PASS_WORK_ID = "TestTransferPass"
        const val BURST = 20
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
