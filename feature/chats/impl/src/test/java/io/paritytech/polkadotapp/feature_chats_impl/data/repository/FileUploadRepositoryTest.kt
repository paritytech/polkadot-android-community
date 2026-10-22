package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.database.dao.FileUploadDao
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class FileUploadRepositoryTest {
    private val dao: FileUploadDao = mock(FileUploadDao::class.java)

    private val repository = FileUploadRepository(dao)

    @Test
    fun `every upload-table write reaches the pass even when the pending ids are unchanged`() = runBlocking<Unit> {
        withPendingIdsInvalidated(listOf("a"), listOf("a"))

        val changes = repository.observeQueueChanges().toList()

        assertEquals(2, changes.size)
    }

    private fun withPendingIdsInvalidated(vararg pendingIdsPerInvalidation: List<String>) {
        whenever(dao.observePendingIds()).thenReturn(flowOf(*pendingIdsPerInvalidation))
    }
}
