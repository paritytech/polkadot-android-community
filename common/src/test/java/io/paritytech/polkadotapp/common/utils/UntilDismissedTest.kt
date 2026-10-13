package io.paritytech.polkadotapp.common.utils

import io.paritytech.polkadotapp.test_shared.settle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UntilDismissedTest {
    private val flag = MutableStateFlow(false)
    private val dismissals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Test
    fun `follows the flag`() = runBlocking<Unit> {
        val seen = collecting {
            flag.value = true
            settle()
            flag.value = false
        }

        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun `a dismissal hides it while the flag stays true`() = runBlocking<Unit> {
        val seen = collecting {
            flag.value = true
            settle()
            dismissals.emit(Unit)
        }

        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun `the next episode shows it again`() = runBlocking<Unit> {
        val seen = collecting {
            flag.value = true
            settle()
            dismissals.emit(Unit)
            settle()
            flag.value = false
            settle()
            flag.value = true
        }

        assertEquals(listOf(false, true, false, true), seen)
    }

    @Test
    fun `a dismissal when nothing is shown does nothing`() = runBlocking<Unit> {
        val seen = collecting {
            dismissals.emit(Unit)
            settle()
            flag.value = true
        }

        assertEquals(listOf(false, true), seen)
    }

    @Test
    fun `two dismissals in one episode are harmless`() = runBlocking<Unit> {
        val seen = collecting {
            flag.value = true
            settle()
            dismissals.emit(Unit)
            settle()
            dismissals.emit(Unit)
        }

        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun `the flag dropping hides it without a dismissal`() = runBlocking<Unit> {
        val seen = collecting {
            flag.value = true
            settle()
            flag.value = false
            settle()
            dismissals.emit(Unit)
        }

        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun `a repeated true after a dismissal does not show it again`() = runBlocking<Unit> {
        val source = MutableSharedFlow<Boolean>()
        val seen = mutableListOf<Boolean>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { source.untilDismissed(dismissals).toList(seen) }
        source.subscriptionCount.first { it > 0 }

        source.emit(true)
        dismissals.subscriptionCount.first { it > 0 }
        dismissals.emit(Unit)
        source.emit(true)
        settle()
        job.cancel()

        assertEquals(listOf(true, false), seen)
    }

    private suspend fun collecting(actions: suspend () -> Unit): List<Boolean> = coroutineScope {
        val seen = mutableListOf<Boolean>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { flag.untilDismissed(dismissals).toList(seen) }

        settle()
        actions()
        settle()
        job.cancel()

        seen
    }
}
