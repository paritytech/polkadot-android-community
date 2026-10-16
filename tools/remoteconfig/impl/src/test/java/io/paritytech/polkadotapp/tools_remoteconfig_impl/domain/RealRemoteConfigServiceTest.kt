package io.paritytech.polkadotapp.tools_remoteconfig_impl.domain

import com.google.gson.Gson
import io.paritytech.polkadotapp.test_shared.testDispatchers
import io.paritytech.polkadotapp.tools_remoteconfig_impl.data.RemoteConfigDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RealRemoteConfigServiceTest {
    private val dataSource = FakeRemoteConfigDataSource()

    @Test
    fun `awaitSynced waits through a failed sync until one succeeds`() = runTest {
        val service = createService()
        withDataSourceSyncOutcomes(false, true)

        val awaiting = async { service.awaitSynced() }
        service.sync()
        runCurrent()
        assertFalse(awaiting.isCompleted)

        service.sync()
        runCurrent()
        assertTrue(awaiting.isCompleted)
    }

    @Test
    fun `synced reads resolve once a sync succeeds`() = runTest {
        val service = createService()
        withDataSourceSyncOutcomes(true)
        withDataSourceString(KEY, VALUE)

        val read = async { service.getSyncedString(KEY) }
        service.sync()

        assertEquals(Result.success(VALUE), read.await())
    }

    @Test
    fun `a sync after a successful one does not fetch again`() = runTest {
        val service = createService()
        withDataSourceSyncOutcomes(true)

        service.sync()
        service.sync()

        assertDataSourceSyncedTimes(1)
    }

    private fun TestScope.createService() = RealRemoteConfigService(
        dataSource = dataSource,
        coroutineDispatchers = testDispatchers(),
        gson = Gson(),
    )

    private fun withDataSourceSyncOutcomes(vararg succeeded: Boolean) {
        dataSource.syncOutcomes += succeeded.toList()
    }

    private fun withDataSourceString(key: String, value: String) {
        dataSource.strings[key] = value
    }

    private fun assertDataSourceSyncedTimes(count: Int) {
        assertEquals(count, dataSource.syncCount)
    }

    private class FakeRemoteConfigDataSource : RemoteConfigDataSource by mock(RemoteConfigDataSource::class.java) {
        val syncOutcomes = ArrayDeque<Boolean>()
        val strings = mutableMapOf<String, String>()
        var syncCount = 0

        override suspend fun sync(): Result<Unit> {
            syncCount++
            return if (syncOutcomes.removeFirst()) Result.success(Unit) else Result.failure(IOException("offline"))
        }

        override suspend fun getString(key: String): String = strings.getValue(key)
    }

    private companion object {
        const val KEY = "key"
        const val VALUE = "value"
    }
}
