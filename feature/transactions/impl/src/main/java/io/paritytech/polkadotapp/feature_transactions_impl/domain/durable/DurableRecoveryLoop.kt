package io.paritytech.polkadotapp.feature_transactions_impl.domain.durable

import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.PinnedChainViewFactory
import io.paritytech.polkadotapp.feature_transactions_impl.data.durable.DurableTxRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformWhile
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Why a pass is being run. Kept for the log — the pass reads the chain the same way whatever woke it. */
private enum class Trigger { LAUNCH, FINALIZED_HEAD, BEST_HEAD, MANUAL_TRIGGER }

/**
 * Runs recovery passes until the ledger holds no live transaction.
 *
 * A pass is worth running exactly when the facts the rules read can have changed: a new best block moves
 * pre-finality completion, a new finalized block moves everything terminal, and a released submission hands
 * a transaction back that the pass had been skipping. All three feed one runner.
 *
 * The stream is conflated, so a pass takes as long as it takes and the heads that arrive meanwhile collapse
 * into a single follow-up rather than queueing into a backlog the loop can never work off.
 *
 * The loop holds no durable state — every pass re-derives everything from the ledger and the chain — so it
 * can be stopped at any point and resumed by being called again.
 */
@Singleton
class DurableRecoveryLoop @Inject constructor(
    private val repository: DurableTxRepository,
    private val recoveryPass: DurableRecoveryPass,
    private val chainViewFactory: PinnedChainViewFactory,
) {
    // Dropping oldest is right: a nudge says "something may be decidable now", and a newer one says it at
    // least as well. What must not happen is a nudge blocking the caller that raised it.
    private val nudges = MutableSharedFlow<Trigger>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Asks for a pass without waiting for a head. Recovery has to be running for this to be acted on — it
     * brings nothing up by itself.
     */
    fun manualTrigger() {
        nudges.tryEmit(Trigger.MANUAL_TRIGGER)
    }

    /**
     * Returns once nothing is live. Fails when a head subscription does, which the caller retries: a lost
     * subscription would otherwise leave live transactions with nothing driving them.
     */
    suspend fun runUntilSettled(): Result<Unit> = runCancellableCatching {
        triggers()
            .conflate()
            .transformWhile { trigger ->
                runPass(trigger)
                emit(Unit)
                hasLiveTransactions()
            }
            .collect()
    }

    /**
     * [onStart] rather than a pass before the subscription: a ledger that settled while the app was dead is
     * decided by the first pass and the loop ends without ever seeing a head.
     */
    private fun triggers() = merge(
        chainViewFactory.finalizedHeads().map { Trigger.FINALIZED_HEAD },
        chainViewFactory.bestHeads().map { Trigger.BEST_HEAD },
        nudges,
    ).onStart { emit(Trigger.LAUNCH) }

    private suspend fun runPass(trigger: Trigger) {
        Timber.d("recovery-trigger $trigger")

        recoveryPass.run().onFailure { Timber.w(it, "recovery-pass-failed trigger=$trigger") }
    }

    /** An unreadable ledger counts as live: abandoning transactions is far worse than one wasted pass. */
    private suspend fun hasLiveTransactions(): Boolean = repository.hasLiveTransactions()
        .onFailure { Timber.w(it, "live-transactions-read-failed") }
        .getOrDefault(true)
}

/**
 * Brings the recovery loop up, wherever it is hosted.
 *
 * A seam rather than a direct `WorkManager` call so that the transaction service — which decides *when*
 * recovery is needed — never has to hold an Android context.
 */
interface DurableRecoveryScheduler {
    /** Idempotent: a no-op while a loop is already running. */
    fun ensureRunning()
}
