package io.paritytech.polkadotapp.feature_device_sync_impl.domain.activeInstallation

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.data.installation.CoinageCurrentInstallationRepository
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallation
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.InstallationStatus
import io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation.ActiveInstallationRecordRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation.ActiveInstallationRecordsPage
import io.paritytech.polkadotapp.feature_device_sync_impl.data.device.DeviceInfo
import io.paritytech.polkadotapp.feature_device_sync_impl.data.device.DeviceInfoProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeySchedule
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeyScheduleProvider
import io.paritytech.polkadotapp.test_shared.FakeTimeProvider
import io.paritytech.polkadotapp.test_shared.TestCoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class RealActiveInstallationManagerTest {
    private val ownId = ByteArray(32) { 0x01 }.toDataByteArray()
    private val otherId = ByteArray(32) { 0xff.toByte() }.toDataByteArray()

    private val recordRepository = FakeActiveInstallationRecordRepository()

    private val manager = RealActiveInstallationManager(
        keyScheduleProvider = FakeDeviceTransferKeyScheduleProvider(),
        currentInstallationRepository = FakeCoinageCurrentInstallationRepository(CoinageInstallationId(ownId)),
        recordRepository = recordRepository,
        deviceInfoProvider = FakeDeviceInfoProvider(),
        timeProvider = FakeTimeProvider { NOW_MILLIS },
        dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    @Test
    fun `with no record on the topic, publishes its own record and becomes active`() = runBlocking<Unit> {
        recordRepository.pages = listOf(page(complete = true))

        manager.followActiveInstallation()

        assertEquals(listOf(installation(ownId, NOW_MILLIS)), recordRepository.published)
        assertEquals(InstallationStatus.Active, manager.status.value)
    }

    @Test
    fun `failed first-install publish is retried until it lands`() = runBlocking<Unit> {
        recordRepository.pages = listOf(page(complete = true))
        recordRepository.publishFailures = 1

        manager.followActiveInstallation()

        assertEquals(2, recordRepository.publishAttempts)
        assertEquals(InstallationStatus.Active, manager.status.value)
    }

    @Test
    fun `another installation's record makes this one inactive without publishing`() = runBlocking<Unit> {
        val other = installation(otherId, activatedAtMillis = 1_000)
        recordRepository.pages = listOf(page(other, complete = true))

        manager.followActiveInstallation()

        assertEquals(InstallationStatus.Inactive(other), manager.status.value)
        assertTrue(recordRepository.published.isEmpty())
    }

    @Test
    fun `installation that finds its own record stays active and doesn't publish again`() = runBlocking<Unit> {
        recordRepository.pages = listOf(page(installation(ownId, activatedAtMillis = 1_000), complete = true))

        manager.followActiveInstallation()

        assertEquals(InstallationStatus.Active, manager.status.value)
        assertTrue(recordRepository.published.isEmpty())
    }

    @Test
    fun `record that can't be read makes the installation unreadable without publishing`() = runBlocking<Unit> {
        recordRepository.pages = listOf(page(complete = true, hasUnreadableRecords = true))

        manager.followActiveInstallation()

        assertEquals(InstallationStatus.Unreadable, manager.status.value)
        assertTrue(recordRepository.published.isEmpty())
    }

    @Test
    fun `unreachable store leaves the status unknown and publishes nothing`() = runBlocking<Unit> {
        recordRepository.pages = listOf(Result.failure(IllegalStateException("Store unreachable")))

        manager.followActiveInstallation()

        assertEquals(InstallationStatus.Unknown, manager.status.value)
        assertTrue(recordRepository.published.isEmpty())
    }

    @Test
    fun `empty first page of a longer history doesn't count as a first install`() = runBlocking<Unit> {
        val other = installation(otherId, activatedAtMillis = 1_000)
        recordRepository.pages = listOf(page(complete = false), page(other, complete = true))

        manager.followActiveInstallation()

        assertEquals(InstallationStatus.Inactive(other), manager.status.value)
        assertTrue(recordRepository.published.isEmpty())
    }

    @Test
    fun `newer record from another phone turns the active installation inactive`() = runBlocking<Unit> {
        val other = installation(otherId, activatedAtMillis = 2_000)
        recordRepository.pages = listOf(
            page(installation(ownId, activatedAtMillis = 1_000), complete = true),
            page(other, complete = true),
        )

        manager.followActiveInstallation()

        assertEquals(InstallationStatus.Inactive(other), manager.status.value)
    }

    @Test
    fun `older record arriving late doesn't take the active status back`() = runBlocking<Unit> {
        recordRepository.pages = listOf(
            page(installation(ownId, activatedAtMillis = 2_000), complete = true),
            page(installation(otherId, activatedAtMillis = 1_000), complete = true),
        )

        manager.followActiveInstallation()

        assertEquals(InstallationStatus.Active, manager.status.value)
    }

    @Test
    fun `records activated at the same time go to the greater installation id`() = runBlocking<Unit> {
        val other = installation(otherId, activatedAtMillis = 1_000)
        recordRepository.pages = listOf(page(installation(ownId, activatedAtMillis = 1_000), other, complete = true))

        manager.followActiveInstallation()

        assertEquals(InstallationStatus.Inactive(other), manager.status.value)
    }

    @Test
    fun `activating this installation publishes its record and makes it active`() = runBlocking<Unit> {
        recordRepository.pages = listOf(page(installation(otherId, activatedAtMillis = 1_000), complete = true))
        manager.followActiveInstallation()

        val result = manager.activateThisInstallation()

        assertTrue(result.isSuccess)
        assertEquals(listOf(installation(ownId, NOW_MILLIS)), recordRepository.published)
        assertEquals(InstallationStatus.Active, manager.status.value)
    }

    private fun page(
        vararg records: ActiveInstallation,
        complete: Boolean,
        hasUnreadableRecords: Boolean = false,
    ): Result<ActiveInstallationRecordsPage> {
        return Result.success(ActiveInstallationRecordsPage(records.toList(), hasUnreadableRecords, complete))
    }

    private fun installation(id: DataByteArray, activatedAtMillis: Long) = ActiveInstallation(
        installationId = id,
        deviceModel = DEVICE_MODEL,
        operatingSystem = OPERATING_SYSTEM,
        activatedAt = Instant.fromEpochMilliseconds(activatedAtMillis),
    )

    private class FakeActiveInstallationRecordRepository : ActiveInstallationRecordRepository {
        var pages: List<Result<ActiveInstallationRecordsPage>> = emptyList()
        var publishFailures = 0
        var publishAttempts = 0
        val published = mutableListOf<ActiveInstallation>()

        override fun subscribeRecords(keys: DeviceTransferKeySchedule): Flow<Result<ActiveInstallationRecordsPage>> {
            return pages.asFlow()
        }

        override suspend fun publishRecord(
            installation: ActiveInstallation,
            keys: DeviceTransferKeySchedule,
        ): Result<Unit> {
            publishAttempts++
            if (publishAttempts <= publishFailures) return Result.failure(IllegalStateException("No allowance"))

            published += installation
            return Result.success(Unit)
        }
    }

    private class FakeDeviceTransferKeyScheduleProvider : DeviceTransferKeyScheduleProvider {
        override suspend fun get(): Result<DeviceTransferKeySchedule> {
            return Result.success(DeviceTransferKeySchedule.fromEntropy(ByteArray(16) { 0x01 }))
        }
    }

    private class FakeCoinageCurrentInstallationRepository(
        private val installationId: CoinageInstallationId,
    ) : CoinageCurrentInstallationRepository {
        override suspend fun getOrCreateCurrent(): CoinageInstallationId = installationId
    }

    private class FakeDeviceInfoProvider : DeviceInfoProvider {
        override fun current(): DeviceInfo = DeviceInfo(model = DEVICE_MODEL, operatingSystem = OPERATING_SYSTEM)
    }

    private companion object {
        const val NOW_MILLIS = 5_000L
        const val DEVICE_MODEL = "Pixel 9"
        const val OPERATING_SYSTEM = "Android 16"
    }
}
