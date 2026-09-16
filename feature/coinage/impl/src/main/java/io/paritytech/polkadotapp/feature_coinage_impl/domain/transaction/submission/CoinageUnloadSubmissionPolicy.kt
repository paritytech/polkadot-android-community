package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.flatten
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageKeyIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetLedger
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.EntryAssets
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogE
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogI
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders.UnloadExtrinsicBuilder
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.AsyncDurableSubmissionPolicy
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableFailureKind
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxEntry
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPreparation
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Builds a payment's recycler unloads from what the ledger recorded for them: the vouchers each one redeems
 * and the coins it mints, in order.
 *
 * Every unload of one call is built together, so they share one pinned block and person proof and each gets
 * its own free unload token. A voucher counts as present while it sits in a recycler: that is where an
 * unload proves it, and a voucher that left one was redeemed by something else.
 */
@OptIn(ExperimentalTime::class)
class CoinageUnloadSubmissionPolicy @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val assetLedger: CoinageAssetLedger,
    private val coinRepository: CoinRepository,
    private val voucherRepository: VoucherRepository,
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
    private val unloadExtrinsicBuilder: UnloadExtrinsicBuilder,
    private val timeProvider: TimeProvider,
) : AsyncDurableSubmissionPolicy {
    override val chainId: ChainId get() = chainAssetProvider.chainId()

    /**
     * A transfer scheduled to be built once is never built again. Otherwise the failure decides: see
     * [retryableFailure]. Whether a rebuild can still land depends on the chain, which only [prepareSubmission]
     * may read.
     */
    override suspend fun canRetry(entry: DurableTxEntry, params: DataByteArray, failure: DurableFailureKind): Boolean {
        val transfer = CoinageSubmissionParams.decodeTransfer(params).getOrNull() ?: return false

        return transfer.retryFailures && retryableFailure(failure, timeProvider.now(), transfer.buildUntil)
    }

    override suspend fun prepareSubmission(
        transactions: List<ScheduledDurableTx>,
    ): Result<Map<DurableTxId, SubmissionPreparation>> = runCancellableCatching {
        val assets = assetLedger.assetsOf(transactions.map { it.id }).getOrElse { return@runCancellableCatching Result.failure(it) }
        val (unloads, unbuildable) = resolveUnloads(transactions, assets)

        if (unloads.isEmpty()) return@runCancellableCatching Result.success(unbuildable.associateWith { SubmissionPreparation.GiveUp })

        val look = awaitInputs(
            presence = voucherRepository.subscribeVouchersInRecycler().map { vouchers ->
                vouchers.mapTo(mutableSetOf()) { it.ringVrfKeyIndex }
            },
            wanted = unloads.flatMapTo(mutableSetOf()) { it.voucherIndices },
            earliestDeadline = unloads.minOfOrNull { it.retryUntil } ?: timeProvider.now(),
            timeProvider = timeProvider,
        )

        val (ready, notReady) = unloads.partition { look.present.containsAll(it.voucherIndices) }
        val abandoned = notReady.filter { unload ->
            unload.voucherIndices.any { look.abandoned(it, unload.retryUntil) }
        }

        abandoned.forEach { coinageLogI("unload-rebuild-abandoned entry=${it.id.value} until=${it.retryUntil}") }

        build(ready).map { built ->
            (built + abandoned.map { it.id to SubmissionPreparation.GiveUp } + unbuildable.map { it to SubmissionPreparation.GiveUp })
                .toMap()
        }
    }.flatten()

    private suspend fun build(ready: List<ScheduledUnload>): Result<List<Pair<DurableTxId, SubmissionPreparation>>> {
        if (ready.isEmpty()) return Result.success(emptyList())

        // Re-read after the look, so every voucher carries the recycler location it is proven in now.
        val vouchers = voucherRepository.getByRingVrfKeyIndices(ready.flatMap { it.voucherIndices })
            .associateBy { it.ringVrfKeyIndex }

        val unloads = ready.map { unload ->
            UnloadExtrinsicBuilder.Unload(
                vouchers = unload.voucherIndices.map { vouchers.getValue(it) },
                outputCoins = unload.outputs,
            )
        }

        val extrinsics = with(StalenessReportCollector.NoOp) {
            unloadExtrinsicBuilder.build(
                unloads = unloads,
                peopleCollection = activePeopleCollectionUseCase.getActivePeopleCollection(),
                chain = chainAssetProvider.chain(),
            )
        }.getOrElse { return Result.failure(it) }

        unloadExtrinsicBuilder.noteUnloadsHappened(extrinsics.size)

        return Result.success(ready.zip(extrinsics) { unload, extrinsic -> unload.id to SubmissionPreparation.Ready(extrinsic) })
    }

    private suspend fun resolveUnloads(
        transactions: List<ScheduledDurableTx>,
        assets: Map<DurableTxId, EntryAssets>,
    ): Pair<List<ScheduledUnload>, List<DurableTxId>> {
        val coinIndices = assets.values.flatMap { entry -> entry.outputs.mapNotNull { (it.asset as? OwnAsset.Coin)?.derivationIndex } }
        val coins = coinRepository.getCoinsBy(coinIndices).associateBy { it.derivationIndex }

        val voucherIndices = assets.values.flatMap { entry -> entry.inputs.mapNotNull { (it.asset as? OwnAsset.Voucher)?.ringVrfIndex } }
        val knownVouchers = voucherRepository.getByRingVrfKeyIndices(voucherIndices)
            .mapTo(mutableSetOf()) { it.ringVrfKeyIndex }

        val unloads = mutableListOf<ScheduledUnload>()
        val unbuildable = mutableListOf<DurableTxId>()

        transactions.forEach { tx ->
            val entry = assets[tx.id]
            val inputs = entry?.inputs?.map { (it.asset as? OwnAsset.Voucher)?.ringVrfIndex }
            val outputs = entry?.outputs?.map { (it.asset as? OwnAsset.Coin)?.let { coin -> coins[coin.derivationIndex] } }
            val params = CoinageSubmissionParams.decodeTransfer(tx.policy.params).getOrNull()

            val resolved = inputs?.filterNotNull()?.takeIf { it.size == inputs.size && it.isNotEmpty() && knownVouchers.containsAll(it) }
            val resolvedOutputs = outputs?.filterNotNull()?.takeIf { it.size == outputs.size }

            if (resolved == null || resolvedOutputs == null || params == null) {
                // Nothing recorded can ever make this buildable, so waiting on it would only hold its lock.
                coinageLogE("unload-rebuild-impossible entry=${tx.id.value} reason=ledger-or-params-unreadable")
                unbuildable += tx.id
            } else {
                unloads += ScheduledUnload(tx.id, resolved, resolvedOutputs, params.buildUntil)
            }
        }

        return unloads to unbuildable
    }

    private data class ScheduledUnload(
        val id: DurableTxId,
        val voucherIndices: List<CoinageKeyIndex>,
        val outputs: List<Coin>,
        val retryUntil: Instant,
    )
}
