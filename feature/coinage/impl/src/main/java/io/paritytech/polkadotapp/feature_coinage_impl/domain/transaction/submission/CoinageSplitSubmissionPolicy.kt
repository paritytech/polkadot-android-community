package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetLedger
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogE
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogI
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders.SplitExtrinsicBuilder
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.AsyncDurableSubmissionPolicy
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxEntry
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPreparation
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Builds a payment's `Coinage.split` from what the ledger recorded for it: the coin it spends and the coins it
 * mints, in order. A rebuild therefore mints exactly the coins whose keys the recipient already holds.
 */
@OptIn(ExperimentalTime::class)
class CoinageSplitSubmissionPolicy @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val assetLedger: CoinageAssetLedger,
    private val coinRepository: CoinRepository,
    private val splitExtrinsicBuilder: SplitExtrinsicBuilder,
    private val timeProvider: TimeProvider,
) : AsyncDurableSubmissionPolicy {
    override val chainId: ChainId get() = chainAssetProvider.chainId()

    /**
     * Only a transfer scheduled with a retry window is built again. Whether its rebuild can still land depends
     * on the chain, which only [prepareSubmission] may read.
     */
    override suspend fun canRetry(entry: DurableTxEntry, params: DataByteArray): Boolean =
        CoinageSubmissionParams.decodeTransfer(params).getOrNull()?.retryUntil != null

    override suspend fun prepareSubmission(
        transactions: List<ScheduledDurableTx>,
    ): Result<Map<DurableTxId, SubmissionPreparation>> = runCatching {
        val (splits, unbuildable) = resolveSplits(transactions)

        val look = awaitInputs(
            presence = coinRepository.subscribeCoinPresence(chainId, splits.map { it.coinToSplit.accountId }),
            wanted = splits.mapTo(mutableSetOf()) { it.coinToSplit.accountId },
            earliestDeadline = splits.minOfOrNull { it.retryUntil } ?: timeProvider.now(),
            timeProvider = timeProvider,
        )

        val chain = chainAssetProvider.chain()

        val decided = splits.mapNotNull { split ->
            val input = split.coinToSplit.accountId

            when {
                input in look.present -> {
                    val extrinsic = splitExtrinsicBuilder.build(chain, split.coinToSplit, split.outputs).getOrThrow()
                    split.id to SubmissionPreparation.Ready(extrinsic)
                }

                look.abandoned(input, split.retryUntil) -> {
                    coinageLogI("split-rebuild-abandoned entry=${split.id.value} until=${split.retryUntil}")
                    split.id to SubmissionPreparation.GiveUp
                }

                else -> null
            }
        }

        (decided + unbuildable.map { it to SubmissionPreparation.GiveUp }).toMap()
    }

    private suspend fun resolveSplits(transactions: List<ScheduledDurableTx>): Pair<List<Split>, List<DurableTxId>> {
        val assets = assetLedger.assetsOf(transactions.map { it.id }).getOrThrow()

        val indices = assets.values.flatMap { entry -> (entry.inputs + entry.outputs).mapNotNull { (it.asset as? OwnAsset.Coin)?.derivationIndex } }
        val coins = coinRepository.getCoinsBy(indices).associateBy { it.derivationIndex }

        val splits = mutableListOf<Split>()
        val unbuildable = mutableListOf<DurableTxId>()

        transactions.forEach { tx ->
            val entry = assets[tx.id]
            val input = (entry?.inputs?.singleOrNull()?.asset as? OwnAsset.Coin)?.let { coins[it.derivationIndex] }
            val outputs = entry?.outputs?.map { (it.asset as? OwnAsset.Coin)?.let { coin -> coins[coin.derivationIndex] } }
            val params = CoinageSubmissionParams.decodeTransfer(tx.policy.params).getOrNull()

            if (input == null || outputs == null || outputs.any { it == null } || params == null) {
                // Nothing recorded can ever make this buildable, so waiting on it would only hold its lock.
                coinageLogE("split-rebuild-impossible entry=${tx.id.value} reason=ledger-or-params-unreadable")
                unbuildable += tx.id
            } else {
                splits += Split(tx.id, input, outputs.filterNotNull(), params.retryUntil ?: timeProvider.now())
            }
        }

        return splits to unbuildable
    }

    private class Split(
        val id: DurableTxId,
        val coinToSplit: Coin,
        val outputs: List<Coin>,
        val retryUntil: Instant,
    )
}
