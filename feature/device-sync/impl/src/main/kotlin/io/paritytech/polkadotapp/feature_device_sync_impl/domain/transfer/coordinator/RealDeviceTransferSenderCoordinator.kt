package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.coordinator

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferFailure
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferRequest
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferSenderCoordinator
import io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer.DeviceTransferState
import io.paritytech.polkadotapp.feature_device_sync_impl.data.transfer.DeviceTransferProcessedRequestStorage
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferConstants
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.connection.DeviceTransferConnectionResult
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.connection.DeviceTransferConnector
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol.DeviceTransferProtocolFactory
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol.DeviceTransferSender
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.protocol.DeviceTransferSenderOutcome
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
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealDeviceTransferSenderCoordinator @Inject constructor(
    private val processedRequestStorage: DeviceTransferProcessedRequestStorage,
    private val sessionFactory: DeviceTransferSessionFactory,
    private val connector: DeviceTransferConnector,
    private val protocolFactory: DeviceTransferProtocolFactory,
    private val timeProvider: TimeProvider,
    dispatchers: CoroutineDispatchers,
) : DeviceTransferSenderCoordinator, CoroutineScope {
    override val coroutineContext = dispatchers.io + SupervisorJob()

    private val mutableState = MutableStateFlow<DeviceTransferState>(DeviceTransferState.Idle)
    override val state: StateFlow<DeviceTransferState> = mutableState.asStateFlow()

    private var runJob: Job? = null

    @Volatile
    private var activeSender: DeviceTransferSender? = null

    @Volatile
    private var activeTransport: DataTransport? = null

    @Synchronized
    override fun confirm(request: DeviceTransferRequest) {
        if (runJob?.isCompleted == false) return

        if (!request.isFresh()) {
            mutableState.value = DeviceTransferState.Failed(DeviceTransferFailure.EXPIRED)
            return
        }

        mutableState.value = DeviceTransferState.Connecting
        runJob = launch { mutableState.value = send(request) }.apply {
            invokeOnCompletion { cause ->
                if (cause is CancellationException) mutableState.value = DeviceTransferState.Failed(DeviceTransferFailure.CANCELLED)
            }
        }
    }

    override fun reject(request: DeviceTransferRequest) {
        launch {
            processedRequestStorage.markProcessed(request.requestId)
            sendRejection(request).logFailure("Device transfer: failed to reject request ${request.requestId}")
        }
    }

    @Synchronized
    override fun cancel() {
        val job = runJob?.takeIf { it.isActive } ?: return

        if (activeTransport?.isOpen() == true) activeSender?.cancel() else job.cancel()
    }

    private suspend fun send(request: DeviceTransferRequest): DeviceTransferState {
        return try {
            transfer(request)
                .logFailure("Device transfer as sender failed")
                .getOrElse { DeviceTransferState.Failed(DeviceTransferFailure.FAILED) }
        } finally {
            activeSender = null
            activeTransport = null
        }
    }

    private suspend fun transfer(request: DeviceTransferRequest): Result<DeviceTransferState> {
        processedRequestStorage.markProcessed(request.requestId)

        return sessionFactory.create(request.requestId, DeviceTransferRole.SENDER, parentScope = this).flatMap { session ->
            runCancellableCatching {
                try {
                    sendOver(session)
                } finally {
                    session.dispose()
                }
            }
        }
    }

    private suspend fun sendOver(session: DeviceTransferSession): DeviceTransferState {
        val sender = protocolFactory.createSender().also { activeSender = it }
        var outcome: DeviceTransferSenderOutcome? = null

        val result = connector.connect(session) { transport ->
            activeTransport = transport
            coroutineScope {
                val openWatching = launch {
                    transport.awaitOpen()
                    mutableState.compareAndSet(DeviceTransferState.Connecting, DeviceTransferState.Transferring)
                }
                outcome = sender.run(transport)
                openWatching.cancel()
            }
        }

        return when (result) {
            DeviceTransferConnectionResult.FINISHED -> outcome.toState()
            DeviceTransferConnectionResult.TIMED_OUT -> DeviceTransferState.Failed(DeviceTransferFailure.TIMED_OUT)
            DeviceTransferConnectionResult.REJECTED -> DeviceTransferState.Failed(DeviceTransferFailure.FAILED)
        }
    }

    private suspend fun sendRejection(request: DeviceTransferRequest): Result<Unit> {
        return sessionFactory.create(request.requestId, DeviceTransferRole.SENDER, parentScope = this).flatMap { session ->
            try {
                session.sendRejection()
            } finally {
                session.dispose()
            }
        }
    }

    private fun DeviceTransferSenderOutcome?.toState(): DeviceTransferState = when (this) {
        DeviceTransferSenderOutcome.Completed -> DeviceTransferState.Completed
        is DeviceTransferSenderOutcome.AbortedByReceiver -> DeviceTransferState.Failed(DeviceTransferFailure.ABORTED)
        DeviceTransferSenderOutcome.Cancelled -> DeviceTransferState.Failed(DeviceTransferFailure.CANCELLED)
        null -> DeviceTransferState.Failed(DeviceTransferFailure.FAILED)
    }

    private fun DeviceTransferRequest.isFresh(): Boolean {
        return timeProvider.now() <= createdAt + DeviceTransferConstants.STATEMENT_LIFETIME
    }
}
