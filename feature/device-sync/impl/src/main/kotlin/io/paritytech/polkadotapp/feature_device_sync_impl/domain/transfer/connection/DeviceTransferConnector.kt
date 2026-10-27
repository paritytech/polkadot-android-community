package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.connection

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.utils.flowOf
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferConstants
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session.DeviceTransferRole
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.session.DeviceTransferSession
import io.paritytech.polkadotapp.tools_media_connection_api.domain.DataTransport
import io.paritytech.polkadotapp.tools_media_connection_api.domain.PeerChannel
import io.paritytech.polkadotapp.tools_media_connection_api.domain.PeerChannelFactory
import io.paritytech.polkadotapp.tools_media_connection_api.domain.PeerConnectionLogger
import io.paritytech.polkadotapp.tools_media_connection_api.domain.models.MediaConfiguration
import io.paritytech.polkadotapp.tools_media_connection_api.domain.models.isTerminal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class DeviceTransferConnector @Inject constructor(
    private val peerChannelFactory: PeerChannelFactory,
    private val timeProvider: TimeProvider,
) {
    suspend fun connect(
        session: DeviceTransferSession,
        useChannel: suspend (DataTransport) -> Unit,
    ): DeviceTransferConnectionResult {
        return when (session.role) {
            DeviceTransferRole.SENDER -> connectUntilFinished(session, useChannel)
            DeviceTransferRole.RECEIVER -> raceFirst(
                { connectUntilFinished(session, useChannel) },
                { awaitRejection(session) },
            )
        }
    }

    private suspend fun connectUntilFinished(
        session: DeviceTransferSession,
        useChannel: suspend (DataTransport) -> Unit,
    ): DeviceTransferConnectionResult {
        var disconnectedSince = timeProvider.now()

        while (timeProvider.now() - disconnectedSince < DeviceTransferConstants.STATEMENT_LIFETIME) {
            val attempt = runCancellableCatching { connectOnce(session, useChannel) }
                .onFailure { Timber.w(it, "Device transfer ${session.requestId}: connection attempt failed") }
                .getOrDefault(ConnectionAttempt.NOT_OPENED)

            when (attempt) {
                ConnectionAttempt.FINISHED -> return DeviceTransferConnectionResult.FINISHED
                ConnectionAttempt.DROPPED -> disconnectedSince = timeProvider.now()
                ConnectionAttempt.NOT_OPENED -> Unit
            }

            delay(RECONNECT_DELAY)
        }

        Timber.w("Device transfer ${session.requestId}: no connection for ${DeviceTransferConstants.STATEMENT_LIFETIME}")
        return DeviceTransferConnectionResult.TIMED_OUT
    }

    private suspend fun connectOnce(
        session: DeviceTransferSession,
        useChannel: suspend (DataTransport) -> Unit,
    ): ConnectionAttempt {
        session.signaling.activeOfferId()?.let(session.signaling::sendReconnected)

        val channel = peerChannelFactory.createSingleConnection(
            signaling = session.signaling,
            mediaConfiguration = MediaConfiguration.None,
            scope = session.createChannelScope(),
            isInitiator = session.role == DeviceTransferRole.SENDER,
            logger = PeerConnectionLogger("device-transfer:${session.requestId}"),
        )
        channel.startConnection()

        return try {
            raceFirst(
                { useOpenedChannel(channel, useChannel) },
                { awaitReconnectRequest(session) },
            )
        } finally {
            channel.dispose()
        }
    }

    private suspend fun useOpenedChannel(
        channel: PeerChannel,
        useChannel: suspend (DataTransport) -> Unit,
    ): ConnectionAttempt {
        if (!channel.awaitOpen()) return ConnectionAttempt.NOT_OPENED

        return raceFirst(
            {
                useChannel(channel.dataTransport)
                ConnectionAttempt.FINISHED
            },
            {
                channel.connectionState.first { it.isTerminal() }
                ConnectionAttempt.DROPPED
            },
        )
    }

    private suspend fun awaitReconnectRequest(session: DeviceTransferSession): ConnectionAttempt {
        session.signaling.awaitReconnectRequest()
        return ConnectionAttempt.DROPPED
    }

    private suspend fun awaitRejection(session: DeviceTransferSession): DeviceTransferConnectionResult {
        session.awaitRejection()
        return DeviceTransferConnectionResult.REJECTED
    }

    private suspend fun PeerChannel.awaitOpen(): Boolean {
        return withTimeoutOrNull(CONNECT_TIMEOUT) {
            val opened = flowOf {
                dataTransport.awaitOpen()
                true
            }
            val failed = connectionState.filter { it.isTerminal() }.map { false }

            merge(opened, failed).first()
        } ?: false
    }

    private suspend fun <T> raceFirst(vararg blocks: suspend () -> T): T = coroutineScope {
        val racers = blocks.map { block -> async { block() } }
        val winner = select { racers.forEach { racer -> racer.onAwait { it } } }
        racers.forEach { it.cancel() }
        winner
    }

    private enum class ConnectionAttempt {
        FINISHED,
        DROPPED,
        NOT_OPENED,
    }

    private companion object {
        val CONNECT_TIMEOUT = 30.seconds
        val RECONNECT_DELAY = 5.seconds
    }
}
