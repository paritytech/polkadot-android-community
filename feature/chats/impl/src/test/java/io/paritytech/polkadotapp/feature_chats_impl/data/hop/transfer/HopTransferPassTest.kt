package io.paritytech.polkadotapp.feature_chats_impl.data.hop.transfer

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.FAILED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class HopTransferPassTest {
    @Test
    fun `a pass is appended when no pass was ever queued`() {
        val policy = passPolicy(emptyList(), NOW)

        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policy)
    }

    @Test
    fun `a pass is appended when the latest pass is running`() {
        val policy = passPolicy(listOf(pass(SUCCEEDED), pass(RUNNING)), NOW)

        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policy)
    }

    @Test
    fun `a pass is appended when every earlier pass has finished`() {
        val policy = passPolicy(listOf(pass(SUCCEEDED), pass(CANCELLED), pass(FAILED)), NOW)

        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policy)
    }

    @Test
    fun `no pass is queued when one is due and waiting for its constraints`() {
        val policy = passPolicy(listOf(pass(ENQUEUED, scheduledAt = NOW)), NOW)

        assertNull(policy)
    }

    @Test
    fun `no pass is queued when one is already queued behind the running pass`() {
        val policy = passPolicy(listOf(pass(RUNNING), pass(BLOCKED)), NOW)

        assertNull(policy)
    }

    @Test
    fun `the waiting pass is replaced when it is delayed past now`() {
        val policy = passPolicy(listOf(pass(ENQUEUED, scheduledAt = NOW + 1.minutes)), NOW)

        assertEquals(ExistingWorkPolicy.REPLACE, policy)
    }

    @Test
    fun `the waiting passes are replaced when a retry ahead of them is backing off`() {
        val policy = passPolicy(listOf(pass(ENQUEUED, scheduledAt = NOW + 1.minutes), pass(BLOCKED)), NOW)

        assertEquals(ExistingWorkPolicy.REPLACE, policy)
    }

    private fun pass(state: WorkInfo.State, scheduledAt: Instant? = null) = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = emptySet(),
        nextScheduleTimeMillis = scheduledAt?.toEpochMilliseconds() ?: Long.MAX_VALUE
    )

    private companion object {
        val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }
}
