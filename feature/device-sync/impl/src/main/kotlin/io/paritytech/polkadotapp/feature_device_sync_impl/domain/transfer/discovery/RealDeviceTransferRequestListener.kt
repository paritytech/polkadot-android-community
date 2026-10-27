package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.discovery

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallationManager
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.InstallationStatus
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequest
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequestListener
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferProcessedRequestStorage
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferRequestRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.ReceivedDeviceTransferMessage
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferConstants
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeyScheduleProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.runningFold
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Instant

class RealDeviceTransferRequestListener @Inject constructor(
    private val activeInstallationManager: ActiveInstallationManager,
    private val keyScheduleProvider: DeviceTransferKeyScheduleProvider,
    private val requestRepository: DeviceTransferRequestRepository,
    private val processedRequestStorage: DeviceTransferProcessedRequestStorage,
    private val timeProvider: TimeProvider,
    private val dispatchers: CoroutineDispatchers,
) : DeviceTransferRequestListener {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePendingRequest(): Flow<DeviceTransferRequest?> {
        return activeInstallationManager.status
            .map { it == InstallationStatus.Active }
            .distinctUntilChanged()
            .flatMapLatest { isActive -> if (isActive) observePendingRequestWhileActive() else flowOf(null) }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)
    }

    override suspend fun markProcessed(requestId: UUID) {
        processedRequestStorage.markProcessed(requestId)
    }

    private fun observePendingRequestWhileActive(): Flow<DeviceTransferRequest?> = flow {
        val keys = keyScheduleProvider.get()
            .logFailure("Failed to derive the device transfer keys")
            .getOrNull()

        if (keys == null) {
            emit(null)
            return@flow
        }

        val discoveredMessages = requestRepository.subscribeMessages(keys)
            .mapNotNull { pageResult ->
                pageResult.onFailure { Timber.w(it, "Failed to read device transfer requests") }.getOrNull()
            }
            .runningFold(DiscoveredMessages(emptyMap(), emptyMap())) { discovered, messages ->
                discovered.with(messages)
            }
        val processedRequestIds = processedRequestStorage.observeProcessedRequestIds()

        emitAll(
            combine(discoveredMessages, processedRequestIds) { discovered, processed ->
                discovered.newestLiveRequest(timeProvider.now(), processed)
            }
        )
    }

    private class DiscoveredMessages(
        private val requests: Map<UUID, ReceivedRequest>,
        private val cancellations: Map<UUID, Instant>,
    ) {
        fun with(messages: List<ReceivedDeviceTransferMessage>): DiscoveredMessages {
            val requests = requests.toMutableMap()
            val cancellations = cancellations.toMutableMap()

            messages.forEach { received ->
                when (val message = received.message) {
                    is DeviceTransferMessage.Request ->
                        requests[message.request.requestId] = ReceivedRequest(message.request, received.expiresAt)

                    is DeviceTransferMessage.Cancel -> cancellations[message.requestId] = received.expiresAt
                }
            }

            return DiscoveredMessages(requests, cancellations)
        }

        fun newestLiveRequest(now: Instant, processed: Set<UUID>): DeviceTransferRequest? {
            return requests.values
                .filter { it.isLive(now) && !isCancelled(it.request.requestId, now) }
                .map { it.request }
                .filter { it.requestId !in processed }
                .maxByOrNull { it.createdAt }
        }

        private fun isCancelled(requestId: UUID, now: Instant): Boolean {
            val cancelExpiresAt = cancellations[requestId] ?: return false
            return cancelExpiresAt > now
        }
    }

    private class ReceivedRequest(
        val request: DeviceTransferRequest,
        val expiresAt: Instant,
    ) {
        fun isLive(now: Instant): Boolean {
            return expiresAt > now && now <= request.createdAt + DeviceTransferConstants.STATEMENT_LIFETIME
        }
    }
}
