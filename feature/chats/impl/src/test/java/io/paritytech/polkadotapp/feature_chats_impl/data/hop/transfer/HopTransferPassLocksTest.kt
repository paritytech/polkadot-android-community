package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class HopTransferPassLocksTest {
    private val passLocks = HopTransferPassLocks()
    private val events = mutableListOf<String>()
    private val firstMayFinish = CompletableDeferred<Unit>()

    @Test
    fun `a pass starts only after the earlier pass of its transfer has finished when both start at once`() = runBlocking<Unit> {
        val passes = startTwoPasses(first = HopTransfer.UPLOAD, second = HopTransfer.UPLOAD)

        finishFirstPass(passes)

        assertEquals(listOf(FIRST_STARTED, FIRST_FINISHED, SECOND_STARTED), events)
    }

    @Test
    fun `a pass starts only after the earlier pass of its transfer has unwound when that pass is stopped`() = runBlocking<Unit> {
        val passes = startTwoPasses(first = HopTransfer.UPLOAD, second = HopTransfer.UPLOAD)

        stopFirstPass(passes)

        assertEquals(listOf(FIRST_STARTED, FIRST_FINISHED, SECOND_STARTED), events)
    }

    @Test
    fun `a pass starts at once when only a pass of another transfer is running`() = runBlocking<Unit> {
        val passes = startTwoPasses(first = HopTransfer.UPLOAD, second = HopTransfer.DOWNLOAD)

        val startedBeforeTheFirstFinished = events.toList()
        finishFirstPass(passes)

        assertEquals(listOf(FIRST_STARTED, SECOND_STARTED), startedBeforeTheFirstFinished)
    }

    private suspend fun CoroutineScope.startTwoPasses(first: HopTransfer, second: HopTransfer): List<Job> {
        val passes = listOf(
            launch {
                passLocks.runPass(first) {
                    events += FIRST_STARTED
                    try {
                        firstMayFinish.await()
                    } finally {
                        withContext(NonCancellable) {
                            yield()
                            events += FIRST_FINISHED
                        }
                    }
                }
            },
            launch { passLocks.runPass(second) { events += SECOND_STARTED } }
        )
        yield()
        return passes
    }

    private suspend fun finishFirstPass(passes: List<Job>) {
        firstMayFinish.complete(Unit)
        withTimeout(JOIN_TIMEOUT) { passes.joinAll() }
    }

    private suspend fun stopFirstPass(passes: List<Job>) {
        passes.first().cancel()
        withTimeout(JOIN_TIMEOUT) { passes.joinAll() }
    }

    private companion object {
        const val FIRST_STARTED = "first started"
        const val FIRST_FINISHED = "first finished"
        const val SECOND_STARTED = "second started"
        val JOIN_TIMEOUT = 5.seconds
    }
}
