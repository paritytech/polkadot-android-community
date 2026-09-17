package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.flatten
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetLedger
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogE
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogI
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.AsyncDurableSubmissionPolicy
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableFailureKind
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxEntry
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPolicyId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPreparation
import kotlin.time.ExperimentalTime

/**
 * Builds coinage transactions from what the ledger recorded for them, once the inputs they spend are present.
 *
 * Every call resolves its transactions, waits for their inputs together, then builds the ones whose inputs are
 * all present — each rebuild consuming and minting exactly what was registered — and gives up on the ones whose
 * inputs are proven gone past their deadline. The rest keep waiting for a later call. What differs between kinds
 * of transaction is [rebuild]'s.
 */
@OptIn(ExperimentalTime::class)
class InputGatedSubmissionPolicy<T : Any, K>(
    private val policyId: SubmissionPolicyId,
    private val rebuild: CoinageRebuild<T, K>,
    private val chainAssetProvider: ChainAssetProvider,
    private val assetLedger: CoinageAssetLedger,
    private val timeProvider: TimeProvider,
) : AsyncDurableSubmissionPolicy {
    override val chainId: ChainId get() = chainAssetProvider.chainId()

    /** Whether a rebuild can still land depends on the chain, which only [prepareSubmission] may read. */
    override suspend fun canRetry(entry: DurableTxEntry, params: DataByteArray, failure: DurableFailureKind): Boolean {
        val terms = rebuild.termsOf(params) ?: return false

        return terms.retriesFailures && retryableFailure(failure, timeProvider.now(), terms.deadline)
    }

    override suspend fun prepareSubmission(
        transactions: List<ScheduledDurableTx>,
    ): Result<Map<DurableTxId, SubmissionPreparation>> = runCancellableCatching {
        val assets = assetLedger.assetsOf(transactions.map { it.id })
            .getOrElse { return@runCancellableCatching Result.failure(it) }

        val resolved = rebuild.resolve(transactions, assets)
        val waiting = transactions.mapNotNull { tx ->
            val transaction = resolved[tx.id]
            val terms = rebuild.termsOf(tx.policy.params)

            if (transaction != null && terms != null) Waiting(tx.id, transaction, rebuild.inputsOf(transaction), terms) else null
        }

        val unbuildable = (transactions.map { it.id } - waiting.map { it.id }.toSet())
            .onEach { coinageLogE("${policyId.value}-rebuild-impossible entry=${it.value} reason=ledger-or-params-unreadable") }
            .associateWith { SubmissionPreparation.GiveUp }

        if (waiting.isEmpty()) return@runCancellableCatching Result.success(unbuildable)

        val inputs = waiting.flatMapTo(mutableSetOf()) { it.inputs }
        val look = awaitInputs(
            presence = rebuild.presence(inputs),
            wanted = inputs,
            earliestDeadline = waiting.minOf { it.terms.deadline },
            timeProvider = timeProvider,
        )

        val (ready, notReady) = waiting.partition { look.present.containsAll(it.inputs) }
        val abandoned = notReady
            .filter { transaction -> transaction.inputs.any { look.abandoned(it, transaction.terms.deadline) } }
            .onEach { coinageLogI("${policyId.value}-rebuild-abandoned entry=${it.id.value} until=${it.terms.deadline}") }
            .associate { it.id to SubmissionPreparation.GiveUp }

        build(ready).map { built -> built + abandoned + unbuildable }
    }.flatten()

    private suspend fun build(ready: List<Waiting<T, K>>): Result<Map<DurableTxId, SubmissionPreparation>> {
        if (ready.isEmpty()) return Result.success(emptyMap())

        return rebuild.build(ready.map { it.transaction }).map { extrinsics ->
            ready.zip(extrinsics) { waiting, extrinsic -> waiting.id to SubmissionPreparation.Ready(extrinsic) }.toMap()
        }
    }

    private class Waiting<T : Any, K>(
        val id: DurableTxId,
        val transaction: T,
        val inputs: Set<K>,
        val terms: RebuildTerms,
    )
}
