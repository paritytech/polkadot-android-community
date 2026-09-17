package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageKeyIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.EntryAssets
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders.UnloadExtrinsicBuilder
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.EnrichedSendableExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * A payment's recycler unloads: the vouchers each one redeems and the coins it mints, in order.
 *
 * Every unload of one call is built together, so they share one pinned block and person proof and each gets its
 * own free unload token. A voucher counts as present while it sits in a recycler: that is where an unload proves
 * it, and a voucher that left one was redeemed by something else.
 */
class UnloadRebuild @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val coinRepository: CoinRepository,
    private val voucherRepository: VoucherRepository,
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
    private val unloadExtrinsicBuilder: UnloadExtrinsicBuilder,
) : CoinageRebuild<UnloadRebuild.Unload, CoinageKeyIndex> {
    class Unload(val voucherIndices: List<CoinageKeyIndex>, val outputs: List<Coin>)

    override fun termsOf(params: DataByteArray): RebuildTerms? =
        CoinageSubmissionParams.decodeTransfer(params).getOrNull()?.let { RebuildTerms(it.buildUntil, it.retryFailures) }

    override suspend fun resolve(
        transactions: List<ScheduledDurableTx>,
        assets: Map<DurableTxId, EntryAssets>,
    ): Map<DurableTxId, Unload> {
        val coinIndices = assets.values.flatMap { entry -> entry.outputs.mapNotNull { (it.asset as? OwnAsset.Coin)?.derivationIndex } }
        val coins = coinRepository.getCoinsBy(coinIndices).associateBy { it.derivationIndex }

        val voucherIndices = assets.values.flatMap { entry -> entry.inputs.mapNotNull { (it.asset as? OwnAsset.Voucher)?.ringVrfIndex } }
        val knownVouchers = voucherRepository.getByRingVrfKeyIndices(voucherIndices).mapTo(mutableSetOf()) { it.ringVrfKeyIndex }

        return transactions.mapNotNull { tx ->
            val entry = assets[tx.id] ?: return@mapNotNull null
            val inputs = entry.inputs.map { (it.asset as? OwnAsset.Voucher)?.ringVrfIndex }
            val outputs = entry.outputs.map { (it.asset as? OwnAsset.Coin)?.let { coin -> coins[coin.derivationIndex] } }

            val resolvedInputs = inputs.filterNotNull().takeIf { it.size == inputs.size && it.isNotEmpty() && knownVouchers.containsAll(it) }
            val resolvedOutputs = outputs.filterNotNull().takeIf { it.size == outputs.size }

            if (resolvedInputs == null || resolvedOutputs == null) null else tx.id to Unload(resolvedInputs, resolvedOutputs)
        }.toMap()
    }

    override fun inputsOf(transaction: Unload): Set<CoinageKeyIndex> = transaction.voucherIndices.toSet()

    override suspend fun presence(inputs: Set<CoinageKeyIndex>): Flow<Set<CoinageKeyIndex>> =
        voucherRepository.subscribeVouchersInRecycler().map { vouchers -> vouchers.mapTo(mutableSetOf()) { it.ringVrfKeyIndex } }

    override suspend fun build(transactions: List<Unload>): Result<List<EnrichedSendableExtrinsic>> {
        // Re-read after the look, so every voucher carries the recycler location it is proven in now.
        val vouchers = voucherRepository.getByRingVrfKeyIndices(transactions.flatMap { it.voucherIndices })
            .associateBy { it.ringVrfKeyIndex }

        val unloads = transactions.map { unload ->
            UnloadExtrinsicBuilder.Unload(
                vouchers = unload.voucherIndices.map { vouchers.getValue(it) },
                outputCoins = unload.outputs,
            )
        }

        return with(StalenessReportCollector.NoOp) {
            unloadExtrinsicBuilder.build(
                unloads = unloads,
                peopleCollection = activePeopleCollectionUseCase.getActivePeopleCollection(),
                chain = chainAssetProvider.chain(),
            )
        }.onSuccess { unloadExtrinsicBuilder.noteUnloadsHappened(it.size) }
    }
}
