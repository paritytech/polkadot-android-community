package io.paritytech.polkadotapp.feature_coinage_impl.domain.installation

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageAccountBackupStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageAccountBackupObserver
import io.paritytech.polkadotapp.feature_coinage_impl.TEST_INSTALLATION
import io.paritytech.polkadotapp.feature_coinage_impl.data.installation.CoinageInstallationRepository
import io.paritytech.polkadotapp.test_shared.awaitCompletes
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class RealCoinageInstallationGateTest {
    private val status = MutableStateFlow<CoinageAccountBackupStatus>(CoinageAccountBackupStatus.Registering)
    private val holds = mutableListOf<Boolean>()
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val backupObserver: CoinageAccountBackupObserver = mock(CoinageAccountBackupObserver::class.java)
    private val installationRepository: CoinageInstallationRepository = mock(CoinageInstallationRepository::class.java)

    private val gate = RealCoinageInstallationGate(
        installationRepository = installationRepository,
        backupObserver = backupObserver,
    )

    @Before
    fun setUp() = runBlocking<Unit> {
        withBackupStatusObserved()
        withCurrentInstallation()
    }

    @After
    fun tearDown() = recorderScope.cancel()

    @Test
    fun `the id is returned at once when the backup is already completed`() = runBlocking<Unit> {
        withHoldRecorded()
        withBackupStatus(CoinageAccountBackupStatus.Completed)

        repeat(3) { assertEquals(TEST_INSTALLATION, awaitCompletes { gate.awaitCurrent() }) }

        assertHoldNeverRaised()
    }

    @Test
    fun `a request while registering waits and puts the gate on hold`() = runBlocking<Unit> {
        withHoldRecorded()
        withBackupStatus(CoinageAccountBackupStatus.Registering)

        val request = requestInstallation()

        assertStillWaiting(request)
        assertOnHold()
        request.cancel()
    }

    @Test
    fun `a request while delayed waits too`() = runBlocking<Unit> {
        withHoldRecorded()
        withBackupStatus(CoinageAccountBackupStatus.Delayed)

        val request = requestInstallation()

        assertStillWaiting(request)
        assertOnHold()
        request.cancel()
    }

    @Test
    fun `completion serves the waiting request and releases the hold`() = runBlocking<Unit> {
        withHoldRecorded()
        withBackupStatus(CoinageAccountBackupStatus.Registering)
        val request = requestInstallation()
        assertStillWaiting(request)

        withBackupStatus(CoinageAccountBackupStatus.Completed)

        assertEquals(TEST_INSTALLATION, awaitCompletes { request.await() })
        assertReleased()
    }

    @Test
    fun `two waiters hold once and release together`() = runBlocking<Unit> {
        withHoldRecorded()
        withBackupStatus(CoinageAccountBackupStatus.Registering)
        val first = requestInstallation()
        val second = requestInstallation()
        assertStillWaiting(first)
        assertStillWaiting(second)
        assertOnHold()

        first.cancel()
        yield()
        assertOnHold()

        withBackupStatus(CoinageAccountBackupStatus.Completed)

        assertEquals(TEST_INSTALLATION, awaitCompletes { second.await() })
        assertReleased()
        assertEquals(listOf(false, true, false), holds)
    }

    @Test
    fun `a cancelled waiter releases its hold`() = runBlocking<Unit> {
        withHoldRecorded()
        withBackupStatus(CoinageAccountBackupStatus.Registering)
        val request = requestInstallation()
        assertStillWaiting(request)
        assertOnHold()

        request.cancel()
        yield()

        assertTrue(request.isCancelled)
        assertReleased()
    }

    @Test
    fun `a waiter that sees registering then delayed is counted once`() = runBlocking<Unit> {
        withHoldRecorded()
        withBackupStatus(CoinageAccountBackupStatus.Registering)
        val request = requestInstallation()
        assertStillWaiting(request)

        withBackupStatus(CoinageAccountBackupStatus.Delayed)
        yield()
        assertStillWaiting(request)
        assertOnHold()

        withBackupStatus(CoinageAccountBackupStatus.Completed)

        assertEquals(TEST_INSTALLATION, awaitCompletes { request.await() })
        assertReleased()
        assertEquals(listOf(false, true, false), holds)
    }

    private fun withBackupStatusObserved() {
        whenever(backupObserver.subscribeStatus()).thenReturn(status)
    }

    private suspend fun withCurrentInstallation() {
        whenever(installationRepository.getOrCreateCurrent()).thenAnswer { TEST_INSTALLATION.value }
    }

    private fun withBackupStatus(value: CoinageAccountBackupStatus) {
        status.value = value
    }

    private fun withHoldRecorded() {
        gate.subscribeOnHold().onEach { holds += it }.launchIn(recorderScope)
    }

    private fun CoroutineScope.requestInstallation(): Deferred<CoinageInstallationId> = async { gate.awaitCurrent() }

    private suspend fun assertStillWaiting(request: Deferred<CoinageInstallationId>) {
        yield()
        assertFalse("the request completed while the backup was pending", request.isCompleted)
    }

    private fun assertOnHold() = assertEquals(true, holds.last())

    private fun assertReleased() = assertEquals(false, holds.last())

    private fun assertHoldNeverRaised() = assertEquals(listOf(false), holds)
}
