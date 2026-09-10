package io.paritytech.polkadotapp.feature_transactions_impl.domain.durable

import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxFacts
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.LedgerView
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.PinnedChainView
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.PinnedChainViewFactory
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.TxCompletionOracle
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.TxDomainId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.Verdict
import io.paritytech.polkadotapp.feature_transactions_impl.data.durable.DurableTxRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One evaluation of every live transaction that submission tracking does not own. */
interface DurableRecoveryPass {
    /** At most one pass runs at a time; a call made while one is running is a no-op. */
    suspend fun run(): Result<Unit>
}

/**
 * Rules run entirely outside the database transaction — the body search can span a whole mortality window,
 * which must never hold a write open. The write is then a compare-and-set against the status the rules were
 * evaluated from, so a status that moved underneath costs that transaction a pass and nothing else.
 *
 * Every domain read a pass needs is issued once, up front, by that domain's oracle. The scope it returns
 * does not suspend, so rule evaluation cannot go back to the chain per transaction.
 */
@Singleton
class RealDurableRecoveryPass @Inject constructor(
    private val repository: DurableTxRepository,
    private val chainViewFactory: PinnedChainViewFactory,
    private val submissionOwned: SubmissionOwnedTransactions,
    private val oracles: Map<String, @JvmSuppressWildcards TxCompletionOracle>,
) : DurableRecoveryPass {
    // One pass at a time. In memory, so a crash takes it with it and the next launch is free to start.
    private val running = Mutex()

    override suspend fun run(): Result<Unit> {
        if (!running.tryLock()) {
            Timber.d("recovery-pass skipped reason=already-running")

            return Result.success(Unit)
        }

        return try {
            runPass()
        } finally {
            running.unlock()
        }
    }

    private suspend fun runPass(): Result<Unit> {
        val domains = repository.liveDomains().getOrElse { return Result.failure(it) }
        if (domains.isEmpty()) {
            Timber.d("recovery-pass skipped reason=nothing-live")

            return Result.success(Unit)
        }

        // Pinning is a chain read, so it happens only once something is actually decidable.
        val view = chainViewFactory.pin().getOrElse { return Result.failure(it) }

        domains.forEach { domain ->
            runDomainPass(domain, view).onFailure {
                Timber.w(it, "recovery-pass domain=${domain.value} failed")
            }
        }

        return Result.success(Unit)
    }

    /**
     * Two rounds, deliberately. A transaction promoted in the first round is exactly the evidence its
     * predecessor needs, and a domain reads statuses when it opens a pass — so the second round sees what
     * the first wrote. Beyond two the loop would have to reach a fixpoint, which passes are cheap enough
     * not to need: the next head runs another one.
     */
    private suspend fun runDomainPass(domain: TxDomainId, view: PinnedChainView): Result<Unit> {
        val wrote = evaluateRound(domain, view).getOrElse { return Result.failure(it) }
        if (wrote == 0) return Result.success(Unit)

        evaluateRound(domain, view).onFailure { return Result.failure(it) }

        return Result.success(Unit)
    }

    /** Returns how many transactions this round wrote. */
    private suspend fun evaluateRound(domain: TxDomainId, view: PinnedChainView): Result<Int> {
        val all = repository.getAllFacts(domain).getOrElse { return Result.failure(it) }

        val decidable = all.filter { it.status.isLive && !submissionOwned.isOwnedBySubmission(it.id) }
        if (decidable.isEmpty()) return Result.success(0)

        val ledger = SnapshotLedgerView(all)
        val scope = oracle(domain).openPass(decidable, ledger, view).getOrElse { return Result.failure(it) }

        // Batched with the domain's own reads rather than per transaction inside Rule 0.
        val canonical = recordedCanonicality(decidable, view)

        var wrote = 0
        decidable.forEach { tx ->
            val outcome = evaluateLadder(tx, scope, view, canonical[tx.id])

            if (outcome is RuleOutcome.Decided && write(tx, outcome.verdict)) wrote++
        }

        Timber.d("recovery-pass domain=${domain.value} decidable=${decidable.size} written=$wrote")

        return Result.success(wrote)
    }

    /**
     * Whether each recorded block is still canonical, one concurrent read per distinct block height.
     *
     * A missing entry is a read that failed, which leaves Rule 0 undecided rather than discarding a record
     * on a transport error.
     */
    private suspend fun recordedCanonicality(
        transactions: List<DurableTxFacts>,
        view: PinnedChainView,
    ): Map<DurableTxId, Boolean> = coroutineScope {
        val recorded = transactions.mapNotNull { tx -> tx.successDetectedAt?.let { tx.id to it } }
        if (recorded.isEmpty()) return@coroutineScope emptyMap()

        val byHeight = recorded.map { it.second.blockNumber }.distinct()
        val hashes = byHeight.map { height -> height to async { view.blockHashAt(height) } }
            .associate { (height, deferred) -> height to deferred.await() }

        buildMap {
            recorded.forEach { (id, block) ->
                val read = hashes[block.blockNumber] ?: return@forEach
                // A chain shorter than the record does not have the block, so the record is stale. Only a
                // failed read is unknown, and an unknown one simply does not appear here.
                read.onSuccess { hash -> put(id, hash == block.blockHash) }
            }
        }
    }

    private suspend fun write(tx: DurableTxFacts, verdict: Verdict): Boolean {
        if (verdict.status == tx.status && verdict.successDetectedAt == tx.successDetectedAt) return false

        return repository.compareAndSetStatus(tx.id, tx.status, verdict)
            .onFailure { Timber.w(it, "verdict-write-failed id=${tx.id.value} to=${verdict.status}") }
            .getOrDefault(false)
    }

    private fun oracle(domain: TxDomainId): TxCompletionOracle =
        oracles[domain.value] ?: TxCompletionOracle.Unobservable
}

private class SnapshotLedgerView(
    override val transactions: List<DurableTxFacts>,
) : LedgerView {
    private val byId: Map<DurableTxId, DurableTxStatus> = transactions.associate { it.id to it.status }

    override fun statusOf(id: DurableTxId): DurableTxStatus? = byId[id]
}
