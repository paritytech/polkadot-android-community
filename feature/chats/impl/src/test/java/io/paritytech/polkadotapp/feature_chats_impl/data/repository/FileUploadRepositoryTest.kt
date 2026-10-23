package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.database.dao.FileUploadDao
import io.paritytech.polkadotapp.test_shared.FakeTimeProvider
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class FileUploadRepositoryTest {
    private val dao: FileUploadDao = mock(FileUploadDao::class.java)

    private val repository = FileUploadRepository(dao, FakeTimeProvider { NOW_MILLIS })

    @Test
    fun `every upload-table write reaches the pass even when the pending ids are unchanged`() = runBlocking<Unit> {
        withPendingIdsInvalidated(listOf("a"), listOf("a"))

        val changes = repository.observeQueueChanges().toList()

        assertEquals(2, changes.size)
    }

    @Test
    fun `the pending query is due by the injected clock when it looks for the next upload`() = runBlocking<Unit> {
        repository.getNextPending(excluding = emptyList())

        verifyPendingQueriedAt(NOW_MILLIS)
    }

    private fun withPendingIdsInvalidated(vararg pendingIdsPerInvalidation: List<String>) {
        whenever(dao.observePendingIds()).thenReturn(flowOf(*pendingIdsPerInvalidation))
    }

    private suspend fun verifyPendingQueriedAt(nowMillis: Long) {
        verify(dao).getNextPending(nowMillis, emptyList())
    }

    private companion object {
        const val NOW_MILLIS = 1_700_000_000_000L
    }
}
