package io.paritytech.polkadotapp.feature_connection_status_impl.domain

import io.novasama.substrate_sdk_android.wsrpc.state.SocketStateMachine.State
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ConnectionPool
import io.paritytech.polkadotapp.common.utils.combine
import io.paritytech.polkadotapp.common.utils.network.NetworkStateService
import io.paritytech.polkadotapp.feature_connection_status_api.domain.ConnectionStatusMonitor
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ConnectionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private val STALE_INITIAL_CONNECT_THRESHOLD = 2.seconds

// Bumping attempt past 1 reuses the existing isRetrying() predicate without
// inventing a new flag for the synthesized "stuck on initial connect" state.
private const val STALE_INITIAL_CONNECT_PROMOTED_ATTEMPT = 2

@OptIn(ExperimentalCoroutinesApi::class)
class RealConnectionStatusMonitor @Inject constructor(
    private val networkStateService: NetworkStateService,
    private val knownChains: KnownChains,
    private val chainRegistry: ChainRegistry,
    private val connectionPool: ConnectionPool,
) : ConnectionStatusMonitor {
    override fun observeStatus(): Flow<ConnectionStatus> {
        val chainIds = monitoredChainIds()
        val perChainStates = chainIds.map(::observeChainSocketState)

        return combine(
            networkStateService.isNetworkAvailable,
            perChainStates.combine(),
        ) { isOnline, chainStates ->
            aggregate(isOnline, chainStates)
        }.distinctUntilChanged()
    }

    private fun monitoredChainIds(): List<ChainId> = listOfNotNull(
        knownChains.people,
        knownChains.assetHub,
        knownChains.bulletIn,
        knownChains.hydration,
    )

    private fun observeChainSocketState(chainId: ChainId): Flow<State?> =
        chainRegistry.chainsById
            .map { connectionPool.getConnectionOrNull(chainId) }
            .distinctUntilChanged()
            .flatMapLatest { connection -> connection?.state ?: flowOf(null) }
            .promoteStaleInitialConnecting()

    // A first-attempt `Connecting` that lingers for more than the threshold means
    // the socket is struggling, so we promote it into a "retrying" shape that the
    // aggregator already understands. transformLatest cancels the delay if the
    // upstream state changes before the threshold elapses.
    private fun Flow<State?>.promoteStaleInitialConnecting(): Flow<State?> = transformLatest { state ->
        emit(state)
        if (state is State.Connecting && state.attempt == 0) {
            delay(STALE_INITIAL_CONNECT_THRESHOLD)
            emit(state.copy(attempt = STALE_INITIAL_CONNECT_PROMOTED_ATTEMPT))
        }
    }

    private fun aggregate(isOnline: Boolean, chainStates: List<State?>): ConnectionStatus {
        val total = chainStates.size
        val connected = chainStates.count { it is State.Connected }

        // A live socket proves connectivity regardless of what the OS reports, so
        // only trust the offline flag when nothing is actually connected.
        if (!isOnline && connected == 0) return ConnectionStatus.Offline

        val anyRetrying = chainStates.any { it.isRetrying() }
        if (anyRetrying) return ConnectionStatus.Connecting(
            retrying = true,
            connectedChains = connected,
            totalChains = total,
        )

        val anyConnectingOrMissing = chainStates.any { it == null || it is State.Connecting }
        if (anyConnectingOrMissing) return ConnectionStatus.Connecting(
            retrying = false,
            connectedChains = connected,
            totalChains = total,
        )

        return ConnectionStatus.Connected
    }

    private fun State?.isRetrying(): Boolean = when (this) {
        is State.WaitingForReconnect -> true
        is State.Connecting -> attempt > 1
        else -> false
    }
}
