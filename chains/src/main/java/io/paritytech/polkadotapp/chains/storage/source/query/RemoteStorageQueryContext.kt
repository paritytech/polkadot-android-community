package io.paritytech.polkadotapp.chains.storage.source.query

import io.novasama.substrate_sdk_android.runtime.RuntimeSnapshot
import io.novasama.substrate_sdk_android.wsrpc.SocketService
import io.novasama.substrate_sdk_android.wsrpc.request.runtime.storage.SubscribeStorageRequest
import io.novasama.substrate_sdk_android.wsrpc.request.runtime.storage.storageChange
import io.novasama.substrate_sdk_android.wsrpc.subscriptionFlow
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.holdingConnection
import io.paritytech.polkadotapp.chains.multiNetwork.getRuntime
import io.paritytech.polkadotapp.chains.multiNetwork.getSocket
import io.paritytech.polkadotapp.chains.multiNetwork.requests.StorageSharedRequestsBuilderFactory
import io.paritytech.polkadotapp.chains.multiNetwork.requests.SubstrateSubscriptionBuilder
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.chains.network.rpc.BulkRetriever
import io.paritytech.polkadotapp.chains.network.rpc.queryKey
import io.paritytech.polkadotapp.chains.network.rpc.retrieveAllValues
import io.paritytech.polkadotapp.common.utils.toMultiSubscription
import io.paritytech.polkadotapp.common.utils.withFlowScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// A subscription collected in the background is otherwise left on a paused socket that never answers
private const val CONNECTION_LABEL = "RemoteStorageSubscription"

class RemoteStorageQueryContextFactory(
    private val chainRegistry: ChainRegistry,
    private val bulkRetriever: BulkRetriever,
    private val storageSharedRequestsBuilderFactory: StorageSharedRequestsBuilderFactory,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
) {
    suspend fun create(
        chainId: ChainId,
        subscriptionBuilder: SubstrateSubscriptionBuilder? = null,
        at: BlockHash? = null,
    ): StorageQueryContext {
        val runtime = chainRegistry.getRuntime(chainId)
        val socketService = chainRegistry.getSocket(chainId)
        return RemoteStorageQueryContext(
            bulkRetriever = bulkRetriever,
            socketService = socketService,
            subscriptionBuilder = subscriptionBuilder,
            storageSharedRequestsBuilderFactory = storageSharedRequestsBuilderFactory,
            chainConnectionRefCounter = chainConnectionRefCounter,
            chainId = chainId,
            at = at,
            runtime = runtime
        )
    }
}

private class RemoteStorageQueryContext(
    private val bulkRetriever: BulkRetriever,
    private val socketService: SocketService,
    private val subscriptionBuilder: SubstrateSubscriptionBuilder?,
    private val storageSharedRequestsBuilderFactory: StorageSharedRequestsBuilderFactory,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
    chainId: ChainId,
    at: BlockHash?,
    runtime: RuntimeSnapshot,
) : BaseStorageQueryContext(chainId, runtime, at) {
    override suspend fun queryKeysByPrefix(
        prefix: String,
        at: BlockHash?,
    ): List<String> {
        return bulkRetriever.retrieveAllKeys(socketService, prefix, at)
    }

    override suspend fun queryEntriesByPrefix(
        prefix: String,
        at: BlockHash?,
    ): Map<String, String?> {
        return bulkRetriever.retrieveAllValues(socketService, prefix, at)
    }

    override suspend fun queryKeys(
        keys: List<String>,
        at: BlockHash?,
    ): Map<String, String?> {
        return bulkRetriever.queryKeys(socketService, keys, at)
    }

    override suspend fun queryKey(
        key: String,
        at: BlockHash?,
    ): String? {
        return bulkRetriever.queryKey(socketService, key, at)
    }

    override fun observeKey(key: String): Flow<StorageUpdate> {
        val updates = subscriptionBuilder?.subscribe(key)?.map {
            StorageUpdate(
                value = it.value,
                at = it.block
            )
        } ?: socketService.subscriptionFlow(SubscribeStorageRequest(key))
            .map {
                val storageChange = it.storageChange()

                StorageUpdate(
                    value = storageChange.getSingleChange(),
                    at = storageChange.block
                )
            }

        return updates.holdingConnection(chainConnectionRefCounter, chainId, CONNECTION_LABEL)
    }

    // TODO To this is not quite efficient implementation as we are de-multiplexing arrived keys into multiple flows (in sdk) and them merging them back
    // Instead, we should allow batch subscriptions on sdk level
    override suspend fun observeKeys(keys: List<String>): Flow<Map<String, String?>> {
        val updates = if (subscriptionBuilder != null) {
            subscribeViaExternalBuilder(keys, subscriptionBuilder)
        } else {
            subscribeViaOwnBuilder(keys)
        }

        return updates.holdingConnection(chainConnectionRefCounter, chainId, CONNECTION_LABEL)
    }

    private fun subscribeViaExternalBuilder(
        keys: List<String>,
        subscriptionBuilder: SubstrateSubscriptionBuilder
    ): Flow<Map<String, String?>> {
        return keys.map { key ->
            subscriptionBuilder.subscribe(key).map { key to it.value }
        }.toMultiSubscription(keys.size)
    }

    private fun subscribeViaOwnBuilder(
        keys: List<String>,
    ): Flow<Map<String, String?>> {
        return withFlowScope { scope ->
            val subscriptionBuilder = storageSharedRequestsBuilderFactory.create(chainId)

            keys.map { key -> subscriptionBuilder.subscribe(key).map { key to it.value } }
                .toMultiSubscription(keys.size)
                .also { subscriptionBuilder.subscribe(scope) }
        }
    }

    override suspend fun observeKeysByPrefix(prefix: String): Flow<Map<String, String?>> {
        TODO("Not yet supported")
    }
}
