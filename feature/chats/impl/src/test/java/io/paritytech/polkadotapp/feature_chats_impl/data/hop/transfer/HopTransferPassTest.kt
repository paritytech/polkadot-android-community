package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.FAILED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HopTransferPassTest {
    @Test
    fun `a pass is queued when no pass was ever queued`() {
        assertTrue(needsNewPass(emptyList()))
    }

    @Test
    fun `a pass is queued when the only pass is already running`() {
        assertTrue(needsNewPass(listOf(SUCCEEDED, RUNNING)))
    }

    @Test
    fun `a pass is queued when every earlier pass has finished`() {
        assertTrue(needsNewPass(listOf(SUCCEEDED, CANCELLED, FAILED)))
    }

    @Test
    fun `no pass is queued when one is waiting for its constraints`() {
        assertFalse(needsNewPass(listOf(ENQUEUED)))
    }

    @Test
    fun `no pass is queued when one is already queued behind the running pass`() {
        assertFalse(needsNewPass(listOf(RUNNING, BLOCKED)))
    }
}
