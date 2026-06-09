package io.paritytech.polkadotapp.chains.multiNetwork.connection

import io.novasama.substrate_sdk_android.wsrpc.SocketService
import io.novasama.substrate_sdk_android.wsrpc.interceptor.WebSocketResponseInterceptor
import io.novasama.substrate_sdk_android.wsrpc.interceptor.WebSocketResponseInterceptor.ResponseDelivery
import io.novasama.substrate_sdk_android.wsrpc.networkStateFlow
import io.novasama.substrate_sdk_android.wsrpc.response.RpcResponse
import io.novasama.substrate_sdk_android.wsrpc.state.SocketStateMachine.State
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.connection.autobalance.NodeAutobalancer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ChainConnectionFactory @Inject constructor(
    private val refCounter: ChainConnectionRefCounter,
    private val nodeAutobalancer: NodeAutobalancer,
    private val socketServiceProvider: Provider<SocketService>,
) {
    suspend fun create(chain: Chain): ChainConnection {
        val connection = ChainConnection(
            socketService = socketServiceProvider.get(),
            refCounter = refCounter,
            nodeAutobalancer = nodeAutobalancer,
            chain = chain
        )

        connection.setup()

        return connection
    }
}

private const val INFURA_ERROR_CODE = -32005
private const val ALCHEMY_ERROR_CODE = 429

private const val BLUST_CAPACITY_ERROR_CODE = -32098
private const val BLUST_RATE_LIMIT_ERROR_CODE = -32097

private val RATE_LIMIT_ERROR_CODES =
    listOf(
        INFURA_ERROR_CODE,
        ALCHEMY_ERROR_CODE,
        BLUST_CAPACITY_ERROR_CODE,
        BLUST_RATE_LIMIT_ERROR_CODE
    )

class ChainConnection internal constructor(
    val socketService: SocketService,
    private val refCounter: ChainConnectionRefCounter,
    nodeAutobalancer: NodeAutobalancer,
    private val chain: Chain,
) : CoroutineScope by CoroutineScope(Dispatchers.Default),
    WebSocketResponseInterceptor {
    val state = socketService.networkStateFlow()
        .stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = State.Disconnected)

    private val responseRequiresNodeChangeFlow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    private val nodeChangeSignal = merge(
        state.nodeChangeEvents(),
        responseRequiresNodeChangeFlow
    ).shareIn(scope = this, started = SharingStarted.Eagerly)

    private val availableNodes = MutableStateFlow(chain.nodes)

    private val currentUrl =
        nodeAutobalancer.connectionUrlFlow(
            chainId = chain.id,
            changeConnectionEventFlow = nodeChangeSignal,
            availableNodesFlow = availableNodes,
        )
            .shareIn(scope = this, started = SharingStarted.Eagerly, replay = 1)

    internal suspend fun setup() {
        socketService.setInterceptor(this)

        observeCurrentNode()

        refCounter.shouldConnectionBeEnabled(chain.id).onEach {
            if (it) {
                socketService.resume()
            } else {
                socketService.pause()
            }
        }.launchIn(this)
    }

    private suspend fun observeCurrentNode() {
        val firstNodeUrl = currentUrl.first()?.saturatedUrl ?: return
        socketService.start(firstNodeUrl, remainPaused = true)

        currentUrl
            .mapNotNull { it?.saturatedUrl }
            .filter { nodeUrl -> actualUrl() != nodeUrl }
            .onEach { nodeUrl -> socketService.switchUrl(nodeUrl) }
            .onEach { nodeUrl -> Timber.d("Switching node in ${chain.name} to $nodeUrl") }
            .launchIn(this)
    }

    fun considerUpdateNodes(nodes: Chain.Nodes) {
        availableNodes.value = nodes
    }

    fun finish() {
        cancel()

        socketService.stop()
    }

    private suspend fun actualUrl(): String? {
        return when (val stateSnapshot = state.first()) {
            is State.WaitingForReconnect -> stateSnapshot.url
            is State.Connecting -> stateSnapshot.url
            is State.Connected -> stateSnapshot.url
            State.Disconnected -> null
            is State.Paused -> stateSnapshot.url
        }
    }

    private fun Flow<State>.nodeChangeEvents(): Flow<Unit> {
        return mapNotNull { stateValue ->
            Unit.takeIf { stateValue.needsAutobalance() }
        }
    }

    private fun State.needsAutobalance() = this is State.WaitingForReconnect && attempt > 1

    override fun onRpcResponseReceived(rpcResponse: RpcResponse): ResponseDelivery {
        val error = rpcResponse.error

        return if (error != null && error.code in RATE_LIMIT_ERROR_CODES) {
            Timber.d("Received rate limit exceeded error code in rpc response. Switching to another node")

            responseRequiresNodeChangeFlow.tryEmit(Unit)

            ResponseDelivery.DROP
        } else {
            ResponseDelivery.DELIVER_TO_SENDER
        }
    }
}
