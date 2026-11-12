package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.coordinator

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallationManager
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.InstallationStatus
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferFailure
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferReceiverCoordinator
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequestPublisher
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferState
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.connection.DeviceTransferConnectionResult
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.connection.DeviceTransferConnector
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.finalization.DeviceTransferFinalizer
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol.DeviceTransferProgress
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol.DeviceTransferProtocolFactory
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol.DeviceTransferReceiver
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol.DeviceTransferReceiverOutcome
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session.DeviceTransferRole
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session.DeviceTransferSession
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session.DeviceTransferSessionFactory
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class RealDeviceTransferReceiverCoordinator @Inject constructor(
    private val activeInstallationManager: ActiveInstallationManager,
    private val requestPublisher: DeviceTransferRequestPublisher,
    private val sessionFactory: DeviceTransferSessionFactory,
    private val connector: DeviceTransferConnector,
    private val protocolFactory: DeviceTransferProtocolFactory,
    private val finalizer: DeviceTransferFinalizer,
    dispatchers: CoroutineDispatchers,
) : DeviceTransferReceiverCoordinator, CoroutineScope {
    private companion object {
        val ACTIVE_INSTALLATION_TIMEOUT = 30.seconds
    }

    override val coroutineContext = dispatchers.io + SupervisorJob()

    private val mutableState = MutableStateFlow<DeviceTransferState>(DeviceTransferState.Idle)
    override val state: StateFlow<DeviceTransferState> = mutableState.asStateFlow()

    private var runJob: Job? = null

    @Volatile
    private var publishedRequestId: String? = null

    @Volatile
    private var activeReceiver: DeviceTransferReceiver? = null

    @Volatile
    private var activeTransport: DataTransport? = null

    @Synchronized
    override fun start() {
        if (runJob?.isCompleted == false) return

        mutableState.value = DeviceTransferState.WaitingForSender
        runJob = launch { mutableState.value = receive() }.apply {
            invokeOnCompletion { cause ->
                if (cause is CancellationException) mutableState.value = DeviceTransferState.Failed(DeviceTransferFailure.CANCELLED)
            }
        }
    }

    @Synchronized
    override fun cancel() {
        val job = runJob?.takeIf { it.isActive } ?: return
        if (mutableState.value == DeviceTransferState.Finalizing) return

        if (activeTransport?.isOpen() == true) activeReceiver?.cancel() else job.cancel()
        publishedRequestId?.let { requestId ->
            launch { requestPublisher.cancelRequest(requestId).logFailure("Device transfer: failed to cancel request $requestId") }
        }
    }

    private suspend fun receive(): DeviceTransferState {
        return try {
            transfer()
                .logFailure("Device transfer as receiver failed")
                .getOrElse { DeviceTransferState.Failed(DeviceTransferFailure.FAILED) }
        } finally {
            publishedRequestId = null
            activeReceiver = null
            activeTransport = null
        }
    }

    private suspend fun transfer(): Result<DeviceTransferState> {
        return awaitSenderInstallationId().flatMap { senderInstallationId ->
            requestPublisher.publishRequest().flatMap { request ->
                publishedRequestId = request.requestId
                sessionFactory.create(request.requestId, DeviceTransferRole.RECEIVER, parentScope = this).flatMap { session ->
                    runCancellableCatching {
                        try {
                            receiveOver(session, senderInstallationId, request.requestId)
                        } finally {
                            session.dispose()
                        }
                    }
                }
            }
        }
    }

    private suspend fun receiveOver(
        session: DeviceTransferSession,
        senderInstallationId: DataByteArray,
        requestId: String,
    ): DeviceTransferState = coroutineScope {
        val receiver = protocolFactory.createReceiver(senderInstallationId).also { activeReceiver = it }
        val peerWatching = launch {
            session.awaitPeerFound()
            mutableState.compareAndSet(DeviceTransferState.WaitingForSender, DeviceTransferState.Connecting)
        }
        val progressMirroring = launch {
            receiver.progress.filterNotNull().collect { progress -> mutableState.value = progress.toState() }
        }

        var outcome: DeviceTransferReceiverOutcome? = null
        val result = connector.connect(session) { transport ->
            activeTransport = transport
            outcome = receiver.run(transport)
        }

        peerWatching.cancel()
        progressMirroring.cancel()

        when (result) {
            DeviceTransferConnectionResult.FINISHED -> finish(outcome, requestId)
            DeviceTransferConnectionResult.REJECTED -> DeviceTransferState.Failed(DeviceTransferFailure.REJECTED)
            DeviceTransferConnectionResult.TIMED_OUT -> DeviceTransferState.Failed(DeviceTransferFailure.TIMED_OUT)
        }
    }

    private suspend fun finish(outcome: DeviceTransferReceiverOutcome?, requestId: String): DeviceTransferState {
        return when (outcome) {
            DeviceTransferReceiverOutcome.Completed -> finalize(requestId)
            is DeviceTransferReceiverOutcome.AbortedBySender -> DeviceTransferState.Failed(DeviceTransferFailure.ABORTED)
            DeviceTransferReceiverOutcome.Cancelled -> DeviceTransferState.Failed(DeviceTransferFailure.CANCELLED)
            null -> DeviceTransferState.Failed(DeviceTransferFailure.FAILED)
        }
    }

    private suspend fun finalize(requestId: String): DeviceTransferState {
        mutableState.value = DeviceTransferState.Finalizing

        return finalizer.finalize(requestId)
            .logFailure("Device transfer: finalization failed")
            .fold(
                onSuccess = { DeviceTransferState.Completed },
                onFailure = { DeviceTransferState.Failed(DeviceTransferFailure.FAILED) },
            )
    }

    private suspend fun awaitSenderInstallationId(): Result<DataByteArray> {
        val activeInstallation = withTimeoutOrNull(ACTIVE_INSTALLATION_TIMEOUT) {
            activeInstallationManager.status.filterIsInstance<InstallationStatus.Inactive>().first().activeInstallation
        }

        return activeInstallation?.let { Result.success(it.installationId) }
            ?: Result.failure(IllegalStateException("No active installation record names another phone"))
    }

    private fun DeviceTransferProgress.toState(): DeviceTransferState = when (this) {
        is DeviceTransferProgress.Syncing -> DeviceTransferState.Syncing(appliedChanges, totalChanges)
        is DeviceTransferProgress.Files -> DeviceTransferState.Files(receivedBytes, totalBytes)
    }
}
