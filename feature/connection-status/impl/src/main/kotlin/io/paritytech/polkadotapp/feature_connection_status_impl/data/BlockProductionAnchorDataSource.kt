package io.paritytech.polkadotapp.feature_connection_status_impl.data

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.chains.network.binding.toBlockNumber
import io.paritytech.polkadotapp.chains.network.rpc.RpcCalls
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSources
import io.paritytech.polkadotapp.chains.storage.source.query.api.queryNonNull
import io.paritytech.polkadotapp.chains.storage.source.query.metadata
import io.paritytech.polkadotapp.chains.storage.typed.now
import io.paritytech.polkadotapp.chains.storage.typed.timestamp
import io.paritytech.polkadotapp.common.utils.flatten
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.scoring.ChainHealthThresholds
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigInteger
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class BlockProductionAnchor(
    val headHeight: Int,
    val chainTimeSpan: Duration,
)

class BlockProductionAnchorUnavailable(reason: Reason) : Exception(reason.name) {
    enum class Reason {
        ChainTooShort,
        InconsistentTimestamps,
        TimedOut,
    }
}

class BlockProductionAnchorDataSource @Inject constructor(
    private val rpcCalls: RpcCalls,
    private val storageDataSources: StorageDataSources,
    private val chainStateRepository: ChainStateRepository,
) {
    suspend fun fetch(chainId: ChainId, blocksBack: Int): Result<BlockProductionAnchor> =
        withTimeoutOrNull(ChainHealthThresholds.ANCHOR_TIMEOUT) { read(chainId, blocksBack) }
            ?: Result.failure(BlockProductionAnchorUnavailable(BlockProductionAnchorUnavailable.Reason.TimedOut))

    private suspend fun read(chainId: ChainId, blocksBack: Int): Result<BlockProductionAnchor> =
        runCancellableCatching { measure(chainId, blocksBack) }.flatten()

    private suspend fun measure(chainId: ChainId, blocksBack: Int): Result<BlockProductionAnchor> {
        val head = chainStateRepository.currentRemoteBlockNumber(chainId)
        val offset = blocksBack.toBlockNumber()

        if (head < offset) {
            return Result.failure(BlockProductionAnchorUnavailable(BlockProductionAnchorUnavailable.Reason.ChainTooShort))
        }

        // Both hashes come off the one resolved height: reading the best hash and the best height
        // separately would leave them a block apart and flatter the span.
        val headTime = timestampAt(chainId, rpcCalls.getBlockHash(chainId, head))
        val previousTime = timestampAt(chainId, rpcCalls.getBlockHash(chainId, head - offset))
        val span = (headTime - previousTime).toLong().milliseconds

        if (span <= Duration.ZERO) {
            return Result.failure(
                BlockProductionAnchorUnavailable(BlockProductionAnchorUnavailable.Reason.InconsistentTimestamps),
            )
        }

        return Result.success(BlockProductionAnchor(headHeight = head.value.toInt(), chainTimeSpan = span))
    }

    private suspend fun timestampAt(chainId: ChainId, blockHash: BlockHash): BigInteger =
        storageDataSources.remote.query(chainId, at = blockHash) { metadata.timestamp.now.queryNonNull() }
}
