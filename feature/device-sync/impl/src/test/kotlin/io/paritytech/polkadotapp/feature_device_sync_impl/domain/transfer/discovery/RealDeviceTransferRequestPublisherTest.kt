package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.discovery

import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequest
import io.paritytech.polkadotapp.feature_device_sync_impl.data.device.DeviceInfo
import io.paritytech.polkadotapp.feature_device_sync_impl.data.device.DeviceInfoProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferRequestRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.ReceivedDeviceTransferMessage
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeySchedule
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeyScheduleProvider
import io.paritytech.polkadotapp.test_shared.FakeTimeProvider
import io.paritytech.polkadotapp.test_shared.TestCoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.time.Instant

class RealDeviceTransferRequestPublisherTest {
    private val repository = FakeDeviceTransferRequestRepository()

    private val publisher = RealDeviceTransferRequestPublisher(
        keyScheduleProvider = FakeDeviceTransferKeyScheduleProvider(),
        requestRepository = repository,
        deviceInfoProvider = FakeDeviceInfoProvider(),
        timeProvider = FakeTimeProvider { NOW_MILLIS },
        dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    @Test
    fun `published request carries this device and the current time`() = runBlocking<Unit> {
        val request = publisher.publishRequest().getOrThrow()

        val expected = DeviceTransferRequest(
            requestId = request.requestId,
            deviceModel = "Pixel 9",
            operatingSystem = "Android 16",
            createdAt = Instant.fromEpochMilliseconds(NOW_MILLIS),
        )
        assertEquals(expected, request)
        assertEquals(listOf(DeviceTransferMessage.Request(expected)), repository.published)
    }

    @Test
    fun `cancelling a request publishes a cancel for its id`() = runBlocking<Unit> {
        val requestId = UUID.randomUUID()

        publisher.cancelRequest(requestId)

        assertEquals(listOf(DeviceTransferMessage.Cancel(requestId)), repository.published)
    }

    @Test
    fun `failed publish is reported`() = runBlocking<Unit> {
        repository.publishResult = Result.failure(IllegalStateException("No allowance"))

        assertTrue(publisher.publishRequest().isFailure)
    }

    private class FakeDeviceTransferRequestRepository : DeviceTransferRequestRepository {
        var publishResult: Result<Unit> = Result.success(Unit)
        val published = mutableListOf<DeviceTransferMessage>()

        override fun subscribeMessages(
            keys: DeviceTransferKeySchedule,
        ): Flow<Result<List<ReceivedDeviceTransferMessage>>> = emptyFlow()

        override suspend fun publishMessage(
            message: DeviceTransferMessage,
            keys: DeviceTransferKeySchedule,
        ): Result<Unit> {
            if (publishResult.isSuccess) published += message
            return publishResult
        }
    }

    private class FakeDeviceTransferKeyScheduleProvider : DeviceTransferKeyScheduleProvider {
        override suspend fun get(): Result<DeviceTransferKeySchedule> {
            return Result.success(DeviceTransferKeySchedule.fromEntropy(ByteArray(16) { 0x01 }))
        }
    }

    private class FakeDeviceInfoProvider : DeviceInfoProvider {
        override fun current(): DeviceInfo = DeviceInfo(model = "Pixel 9", operatingSystem = "Android 16")
    }

    private companion object {
        const val NOW_MILLIS = 1_700_000_000_000L
    }
}
