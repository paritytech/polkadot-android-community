package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.discovery

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequest
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequestPublisher
import io.paritytech.polkadotapp.feature_device_sync_impl.data.device.DeviceInfoProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferRequestRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeyScheduleProvider
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Instant

class RealDeviceTransferRequestPublisher @Inject constructor(
    private val keyScheduleProvider: DeviceTransferKeyScheduleProvider,
    private val requestRepository: DeviceTransferRequestRepository,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val timeProvider: TimeProvider,
    private val dispatchers: CoroutineDispatchers,
) : DeviceTransferRequestPublisher {
    override suspend fun publishRequest(): Result<DeviceTransferRequest> = withContext(dispatchers.io) {
        val deviceInfo = deviceInfoProvider.current()
        val request = DeviceTransferRequest(
            requestId = UUID.randomUUID(),
            deviceModel = deviceInfo.model,
            operatingSystem = deviceInfo.operatingSystem,
            createdAt = Instant.fromEpochMilliseconds(timeProvider.now().toEpochMilliseconds()),
        )

        publish(DeviceTransferMessage.Request(request)).map { request }
    }

    override suspend fun cancelRequest(requestId: UUID): Result<Unit> = withContext(dispatchers.io) {
        publish(DeviceTransferMessage.Cancel(requestId))
    }

    private suspend fun publish(message: DeviceTransferMessage): Result<Unit> {
        return keyScheduleProvider.get().flatMap { keys -> requestRepository.publishMessage(message, keys) }
    }
}
