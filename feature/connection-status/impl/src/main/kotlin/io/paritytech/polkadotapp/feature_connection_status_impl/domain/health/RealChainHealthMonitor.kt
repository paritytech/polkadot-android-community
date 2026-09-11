package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import io.novasama.substrate_sdk_android.wsrpc.state.SocketStateMachine.State
import io.novasama.substrate_sdk_android.wsrpc.state.pendingRequests
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ConnectionPool
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.common.utils.combine
import io.paritytech.polkadotapp.common.utils.network.NetworkStateService
import io.paritytech.polkadotapp.feature_connection_status_api.domain.ChainHealthMonitor
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_impl.data.ChainHeadDataSource
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.probe.ChainHealthProbe
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.probe.ChainMetricContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Builds a per-chain [ChainHealth] from the smoothed socket state and the readings of the pluggable
 * probe set. The whole per-chain pipeline runs only while collected (foreground) and keeps the socket
 * up via the ref counter for as long as it is subscribed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RealChainHealthMonitor @Inject constructor(
    private val knownChains: KnownChains,
    private val chainRegistry: ChainRegistry,
    private val connectionPool: ConnectionPool,
    private val chainStateRepository: ChainStateRepository,
    private val chainHeadDataSource: ChainHeadDataSource,
    private val connectionRefCounter: ChainConnectionRefCounter,
    private val connectionSmoother: ConnectionSmoother,
    private val networkStateService: NetworkStateService,
    private val probes: Set<@JvmSuppressWildcards ChainHealthProbe>,
) : ChainHealthMonitor {
    override fun observeChainsHealth(): Flow<List<ChainHealth>> =
        monitoredChainIds().map(::observeChainHealth).combine()

    private fun monitoredChainIds(): List<ChainId> = listOf(
        knownChains.people,
        knownChains.assetHub,
        knownChains.bulletIn,
    )

    private fun observeChainHealth(chainId: ChainId): Flow<ChainHealth> = channelFlow {
        connectionRefCounter.withConnectionEnabled(chainId, CONNECTION_LABEL) {
            val chain = chainRegistry.getChain(chainId)
            val blockTime = resolveBlockTime(chainId)

            val bestBlock = chainHeadDataSource.bestBlockNumber(chainId)
                .shareIn(this@channelFlow, SharingStarted.WhileSubscribed(), replay = 1)
            val finalizedBlock = chainHeadDataSource.finalizedBlockNumber(chainId)
                .shareIn(this@channelFlow, SharingStarted.WhileSubscribed(), replay = 1)

            val connection = connectionSmoother.smooth(observeSocketState(chainId))
                .shareIn(this@channelFlow, SharingStarted.WhileSubscribed(), replay = 1)

            val context = ChainMetricContext(
                chain = chain,
                bestBlockNumber = bestBlock,
                finalizedBlockNumber = finalizedBlock,
                expectedBlockTime = blockTime,
                pendingRequests = observePendingRequests(chainId),
                connection = connection,
            )
            val readings = probes.map { it.observe(context) }.combine()

            combine(connection, readings) { presentation, readingList ->
                ChainHealth(
                    chainId = chainId,
                    chainName = chain.name,
                    connection = presentation,
                    expectedBlockTime = blockTime,
                    readings = readingList,
                )
            }.collect { send(it) }
        }
    }

    private fun observeSocketState(chainId: ChainId): Flow<RawConnectivity> =
        chainRegistry.chainsById
            .map { connectionPool.getConnectionOrNull(chainId) }
            .distinctUntilChanged()
            .flatMapLatest { connection -> connection?.state ?: flowOf(null) }
            .let { socketStates -> rawConnectivity(socketStates, networkStateService.isNetworkAvailable) }

    private fun observePendingRequests(chainId: ChainId): Flow<Set<Any>> =
        chainRegistry.chainsById
            .map { connectionPool.getConnectionOrNull(chainId) }
            .distinctUntilChanged()
            .flatMapLatest { connection -> connection?.state ?: flowOf(null) }
            .map { state -> state?.pendingRequests.orEmpty() }

    private suspend fun resolveBlockTime(chainId: ChainId): Duration =
        runCatching { chainStateRepository.expectedBlockTime(chainId) }
            .getOrDefault(FALLBACK_BLOCK_TIME)

    private companion object {
        const val CONNECTION_LABEL = "chain-health"
        val FALLBACK_BLOCK_TIME: Duration = 6.seconds
    }
}

// A socket stuck reconnecting reads as pending forever, whether the node is slow or the phone is
// offline. With no network there is nothing to reconnect to, so the chain counts as down instead.
internal fun State?.toRawConnectivity(deviceOnline: Boolean): RawConnectivity =
    if (deviceOnline) toSocketConnectivity() else RawConnectivity.Settled

// Both operators are load-bearing. A handover drops the active network for a moment while every socket
// stays up, and an offline socket retries several times a second, which would otherwise keep restarting
// the smoother's cooldown before it could report a dead chain.
@OptIn(FlowPreview::class)
internal fun rawConnectivity(
    socketStates: Flow<State?>,
    deviceOnline: Flow<Boolean>,
    networkDebounce: Duration = NETWORK_DEBOUNCE,
): Flow<RawConnectivity> = socketStates
    .combine(deviceOnline.debounce(networkDebounce)) { state, online -> state.toRawConnectivity(online) }
    .distinctUntilChanged()

private fun State?.toSocketConnectivity(): RawConnectivity = when (this) {
    is State.Connected -> RawConnectivity.Connected
    is State.Connecting, is State.WaitingForReconnect -> RawConnectivity.Pending
    null, is State.Disconnected, is State.Paused -> RawConnectivity.Settled
}

private val NETWORK_DEBOUNCE: Duration = 1.seconds
