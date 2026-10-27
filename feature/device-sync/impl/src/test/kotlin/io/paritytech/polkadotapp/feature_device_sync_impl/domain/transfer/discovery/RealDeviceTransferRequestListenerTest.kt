package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.discovery

import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallation
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallationManager
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.InstallationStatus
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequest
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferProcessedRequestStorage
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferRequestRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.ReceivedDeviceTransferMessage
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeySchedule
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeyScheduleProvider
import io.paritytech.polkadotapp.test_shared.FakeTimeProvider
import io.paritytech.polkadotapp.test_shared.TestCoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RealDeviceTransferRequestListenerTest {
    private var nowMillis = NOW_MILLIS
    private val status = MutableStateFlow<InstallationStatus>(InstallationStatus.Active)
    private val messages = MutableSharedFlow<Result<List<ReceivedDeviceTransferMessage>>>(replay = 16)
    private val processedRequestIds = MutableStateFlow<Set<UUID>>(emptySet())

    private val listener = RealDeviceTransferRequestListener(
        activeInstallationManager = FakeActiveInstallationManager(status),
        keyScheduleProvider = FakeDeviceTransferKeyScheduleProvider(),
        requestRepository = FakeDeviceTransferRequestRepository(messages),
        processedRequestStorage = FakeDeviceTransferProcessedRequestStorage(processedRequestIds),
        timeProvider = FakeTimeProvider { nowMillis },
        dispatchers = TestCoroutineDispatchers(Dispatchers.Unconfined),
    )

    @Test
    fun `published request is offered to the active phone`() = withPendingRequests { pending ->
        val request = request(createdAt = now())

        messages.emit(page(request.received(expiresIn = 15.minutes)))

        assertEquals(request, pending.last())
    }

    @Test
    fun `cancel withdraws the offered request`() = withPendingRequests { pending ->
        val request = request(createdAt = now())
        messages.emit(page(request.received(expiresIn = 15.minutes)))

        messages.emit(page(cancel(request.requestId, expiresIn = 15.minutes)))

        assertNull(pending.last())
    }

    @Test
    fun `newest of several requests is offered`() = withPendingRequests { pending ->
        val older = request(createdAt = now())
        val newest = request(createdAt = now() + 10.seconds)

        messages.emit(page(newest.received(expiresIn = 15.minutes), older.received(expiresIn = 15.minutes)))

        assertEquals(newest, pending.last())
    }

    @Test
    fun `request is withdrawn once its statement expires`() = withPendingRequests { pending ->
        val request = request(createdAt = now())
        messages.emit(page(request.received(expiresIn = 60.seconds)))

        nowMillis += 61.seconds.inWholeMilliseconds
        messages.emit(page(cancel(UUID.randomUUID(), expiresIn = 15.minutes)))

        assertNull(pending.last())
    }

    @Test
    fun `request is withdrawn once it outlives the statement lifetime, though its statement is live`() =
        withPendingRequests { pending ->
            val request = request(createdAt = now())
            messages.emit(page(request.received(expiresIn = 60.minutes)))

            nowMillis += (15.minutes + 1.seconds).inWholeMilliseconds
            messages.emit(page(cancel(UUID.randomUUID(), expiresIn = 15.minutes)))

            assertNull(pending.last())
        }

    @Test
    fun `processed request isn't offered again after a relaunch`() {
        val request = request(createdAt = now())
        processedRequestIds.value = setOf(request.requestId)

        withPendingRequests { pending ->
            messages.emit(page(request.received(expiresIn = 15.minutes)))

            assertNull(pending.last())
        }
    }

    @Test
    fun `marking the offered request processed withdraws it`() = withPendingRequests { pending ->
        val request = request(createdAt = now())
        messages.emit(page(request.received(expiresIn = 15.minutes)))

        listener.markProcessed(request.requestId)

        assertNull(pending.last())
    }

    @Test
    fun `inactive installation doesn't follow requests`() {
        status.value = InstallationStatus.Inactive(otherInstallation())

        withPendingRequests { pending ->
            messages.emit(page(request(createdAt = now()).received(expiresIn = 15.minutes)))

            assertEquals(listOf(null), pending)
            assertEquals(0, messages.subscriptionCount.value)
        }
    }

    private fun withPendingRequests(block: suspend (List<DeviceTransferRequest?>) -> Unit) = runBlocking<Unit> {
        val pending = mutableListOf<DeviceTransferRequest?>()
        val collection = launch(Dispatchers.Unconfined) { listener.observePendingRequest().collect { pending += it } }

        try {
            block(pending)
        } finally {
            collection.cancel()
        }
    }

    private fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)

    private fun request(createdAt: Instant) = DeviceTransferRequest(
        requestId = UUID.randomUUID(),
        deviceModel = "Pixel 9",
        operatingSystem = "Android 16",
        createdAt = createdAt,
    )

    private fun DeviceTransferRequest.received(expiresIn: Duration) = ReceivedDeviceTransferMessage(
        message = DeviceTransferMessage.Request(this),
        expiresAt = now() + expiresIn,
    )

    private fun cancel(requestId: UUID, expiresIn: Duration) = ReceivedDeviceTransferMessage(
        message = DeviceTransferMessage.Cancel(requestId),
        expiresAt = now() + expiresIn,
    )

    private fun page(vararg messages: ReceivedDeviceTransferMessage) = Result.success(messages.toList())

    private fun otherInstallation() = ActiveInstallation(
        installationId = ByteArray(32) { 0x02 }.toDataByteArray(),
        deviceModel = "iPhone 15",
        operatingSystem = "iOS 26.0",
        activatedAt = now(),
    )

    private class FakeActiveInstallationManager(
        override val status: StateFlow<InstallationStatus>,
    ) : ActiveInstallationManager {
        override suspend fun activateThisInstallation(): Result<Unit> = Result.success(Unit)
    }

    private class FakeDeviceTransferKeyScheduleProvider : DeviceTransferKeyScheduleProvider {
        override suspend fun get(): Result<DeviceTransferKeySchedule> {
            return Result.success(DeviceTransferKeySchedule.fromEntropy(ByteArray(16) { 0x01 }))
        }
    }

    private class FakeDeviceTransferRequestRepository(
        private val messages: Flow<Result<List<ReceivedDeviceTransferMessage>>>,
    ) : DeviceTransferRequestRepository {
        override fun subscribeMessages(
            keys: DeviceTransferKeySchedule,
        ): Flow<Result<List<ReceivedDeviceTransferMessage>>> = messages

        override suspend fun publishMessage(
            message: DeviceTransferMessage,
            keys: DeviceTransferKeySchedule,
        ): Result<Unit> = Result.success(Unit)
    }

    private class FakeDeviceTransferProcessedRequestStorage(
        private val processedRequestIds: MutableStateFlow<Set<UUID>>,
    ) : DeviceTransferProcessedRequestStorage {
        override fun observeProcessedRequestIds(): Flow<Set<UUID>> = processedRequestIds

        override suspend fun markProcessed(requestId: UUID) {
            processedRequestIds.update { it + requestId }
        }
    }

    private companion object {
        const val NOW_MILLIS = 1_700_000_000_000L
    }
}
