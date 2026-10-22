package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flattenResult
import io.paritytech.polkadotapp.common.utils.mapAsync
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.CoinKeypairDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.EntryAssets
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.asCoinOrNull
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders.ClaimExtrinsicBuilder
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.EnrichedSendableExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ReclaimRebuild @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val coinRepository: CoinRepository,
    private val coinKeypairDerivation: CoinKeypairDerivation,
    private val claimExtrinsicBuilder: ClaimExtrinsicBuilder,
) : CoinageRebuild<ReclaimRebuild.Reclaim, AccountId> {
    class Reclaim(val coin: Coin, val destination: AccountId)

    override fun termsOf(params: DataByteArray): RebuildTerms? =
        CoinageSubmissionParams.decodeTransfer(params).getOrNull()?.let { RebuildTerms(it.buildUntil, it.retryFailures) }

    override suspend fun resolve(
        transactions: List<ScheduledDurableTx>,
        assets: Map<DurableTxId, EntryAssets>,
    ): Map<DurableTxId, Reclaim> {
        val indices = assets.values.mapNotNull { it.inputs.singleOrNull()?.asCoinOrNull()?.derivationIndex }
        val coins = coinRepository.getCoinsBy(indices).associateBy { it.derivationIndex }

        return transactions.mapNotNull { tx ->
            val entry = assets[tx.id] ?: return@mapNotNull null
            val coin = entry.inputs.singleOrNull()?.asCoinOrNull()?.let { coins[it.derivationIndex] } ?: return@mapNotNull null
            val destination = entry.outputs.singleOrNull()?.publicKey ?: return@mapNotNull null

            tx.id to Reclaim(coin, destination)
        }.toMap()
    }

    override fun inputsOf(transaction: Reclaim): Set<AccountId> = setOf(transaction.coin.accountId)

    override suspend fun presence(inputs: Set<AccountId>): Flow<Set<AccountId>> =
        coinRepository.subscribeCoinPresence(chainAssetProvider.chainId(), inputs.toList())

    override suspend fun build(transactions: List<Reclaim>): Result<List<EnrichedSendableExtrinsic>> =
        runCancellableCatching {
            coinKeypairDerivation.deriveKeypairs(transactions.map { it.coin.derivationIndex })
        }.flatMap { keypairs ->
            val chain = chainAssetProvider.chain()

            transactions.zip(keypairs)
                .mapAsync { (reclaim, keypair) -> claimExtrinsicBuilder.build(chain, keypair, reclaim.destination) }
                .flattenResult()
        }
}
