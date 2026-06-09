package io.paritytech.polkadotapp.feature_transactions_impl.data

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.runtime.definitions.types.generics.Era
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.toBlockNumber
import io.paritytech.polkadotapp.chains.network.rpc.RpcCalls
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.invoke
import io.paritytech.polkadotapp.feature_transactions.api.data.Mortality
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.lang.Integer.min
import javax.inject.Inject
import javax.inject.Singleton

private const val FALLBACK_MAX_HASH_COUNT = 250
private const val MAX_FINALITY_LAG = 5
private const val MORTAL_PERIOD = 5 * 60 * 1000

@Singleton
class MortalityConstructor @Inject constructor(
    private val chainStateRepository: ChainStateRepository,
    private val coroutineDispatchers: CoroutineDispatchers
) {
    fun mortalPeriodMillis(): Long = MORTAL_PERIOD.toLong()

    suspend fun constructMortality(
        chainId: ChainId,
        rpcCalls: RpcCalls
    ): Mortality = withContext(coroutineDispatchers.io) {
        val finalizedHash = async { rpcCalls.getFinalizedHead(chainId) }

        val bestHeader = async { rpcCalls.getBlockHeader(chainId) }
        val finalizedHeader = async { rpcCalls.getBlockHeader(chainId, finalizedHash()) }

        val currentHeader = async { bestHeader().parentHash?.let { rpcCalls.getBlockHeader(chainId, it) } ?: bestHeader() }

        val currentNumber = currentHeader().number
        val finalizedNumber = finalizedHeader().number

        val startBlockNumber = if (currentNumber - finalizedNumber > MAX_FINALITY_LAG) currentNumber else finalizedNumber

        val blockHashCount = chainStateRepository.blockHashCount(chainId)?.toInt()

        val blockTime = chainStateRepository.expectedBlockTime(chainId).inWholeMilliseconds.toInt()

        val mortalPeriod = MORTAL_PERIOD / blockTime + MAX_FINALITY_LAG

        val unmappedPeriod = min(blockHashCount ?: FALLBACK_MAX_HASH_COUNT, mortalPeriod)

        val era = Era.getEraFromBlockPeriod(startBlockNumber, unmappedPeriod)
        val eraBlockNumber = ((startBlockNumber - era.phase) / era.period) * era.period + era.phase

        val eraBlockHash = rpcCalls.getBlockHash(chainId, eraBlockNumber.toBlockNumber())
            .fromHex().toDataByteArray()

        Mortality(era, eraBlockHash)
    }
}
