package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.mapAsync
import io.paritytech.polkadotapp.common.utils.flattenResult
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.EntryAssets
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders.SplitExtrinsicBuilder
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.EnrichedSendableExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * A payment's `Coinage.split`: the coin it spends and the coins it mints, in order. A rebuild therefore mints
 * exactly the coins whose keys the recipient already holds.
 */
class SplitRebuild @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val coinRepository: CoinRepository,
    private val splitExtrinsicBuilder: SplitExtrinsicBuilder,
) : CoinageRebuild<SplitRebuild.Split, AccountId> {
    class Split(val coinToSplit: Coin, val outputs: List<Coin>)

    override fun termsOf(params: DataByteArray): RebuildTerms? =
        CoinageSubmissionParams.decodeTransfer(params).getOrNull()?.let { RebuildTerms(it.buildUntil, it.retryFailures) }

    override suspend fun resolve(
        transactions: List<ScheduledDurableTx>,
        assets: Map<DurableTxId, EntryAssets>,
    ): Map<DurableTxId, Split> {
        val indices = assets.values.flatMap { entry ->
            (entry.inputs + entry.outputs).mapNotNull { (it.asset as? OwnAsset.Coin)?.derivationIndex }
        }
        val coins = coinRepository.getCoinsBy(indices).associateBy { it.derivationIndex }

        return transactions.mapNotNull { tx ->
            val entry = assets[tx.id] ?: return@mapNotNull null
            val input = (entry.inputs.singleOrNull()?.asset as? OwnAsset.Coin)?.let { coins[it.derivationIndex] }
            val outputs = entry.outputs.map { (it.asset as? OwnAsset.Coin)?.let { coin -> coins[coin.derivationIndex] } }

            if (input == null || outputs.any { it == null }) null else tx.id to Split(input, outputs.filterNotNull())
        }.toMap()
    }

    override fun inputsOf(transaction: Split): Set<AccountId> = setOf(transaction.coinToSplit.accountId)

    override suspend fun presence(inputs: Set<AccountId>): Flow<Set<AccountId>> =
        coinRepository.subscribeCoinPresence(chainAssetProvider.chainId(), inputs.toList())

    override suspend fun build(transactions: List<Split>): Result<List<EnrichedSendableExtrinsic>> {
        val chain = chainAssetProvider.chain()

        return transactions.mapAsync { splitExtrinsicBuilder.build(chain, it.coinToSplit, it.outputs) }.flattenResult()
    }
}
