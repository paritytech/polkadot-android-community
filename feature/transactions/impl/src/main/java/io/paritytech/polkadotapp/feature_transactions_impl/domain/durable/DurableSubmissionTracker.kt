package io.paritytech.polkadotapp.feature_transactions_impl.domain.durable

import io.novasama.substrate_sdk_android.runtime.extrinsic.signer.SendableExtrinsic
import io.paritytech.polkadotapp.chains.extrinsic.ExtrinsicStatus
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.chains.multiNetwork.getChain
import io.paritytech.polkadotapp.chains.multiNetwork.runtime.repository.ExtrinsicOutcome
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.PreSubmissionValidationFailed
import io.paritytech.polkadotapp.feature_transactions.api.data.retry.ResubmitWhenValidFactory
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.CheckpointBlock
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.PinnedChainViewFactory
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.Verdict
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableChainProvider
import io.paritytech.polkadotapp.feature_transactions_impl.data.durable.DurableTxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Follows one extrinsic from submission. Its purpose is latency: it learns of inclusion and finality faster
 * than polling would.
 *
 * It proposes; it does not write. Every status change goes through the same compare-and-set the recovery
 * pass uses, so the guards apply uniformly.
 *
 * It owns the transactions it watches, and ownership is one-shot: released exactly once, never taken back —
 * not on a resubmission, not on anything. Release drops it, stops the subscription and triggers a pass.
 */
class DurableSubmissionTracker @Inject constructor(
    private val chainProvider: DurableChainProvider,
    private val chainRegistry: ChainRegistry,
    private val extrinsicService: ExtrinsicService,
    private val repository: DurableTxRepository,
    private val chainViewFactory: PinnedChainViewFactory,
    private val submissionOwned: SubmissionOwnedTransactions,
    private val resubmitWhenValidFactory: ResubmitWhenValidFactory,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
) {
    fun watch(
        scope: CoroutineScope,
        id: DurableTxId,
        extrinsic: SendableExtrinsic,
        onReleased: () -> Unit,
    ) {
        scope.launch {
            runCatching { follow(id, extrinsic) }
                .onFailure { Timber.w(it, "tx=${id.value} submission-watch-failed") }

            submissionOwned.release(id)

            // Recovery is for transactions nobody has decided. A watch that ended by writing a terminal
            // verdict has already done the deciding, and a terminal row is never rewritten — so asking for
            // a pass would schedule a worker and pin a chain view to re-derive an answer that exists.
            if (needsRecovery(id)) onReleased()
        }
    }

    /** Unreadable counts as needing recovery: the pass re-reads it, where guessing here could strand it. */
    private suspend fun needsRecovery(id: DurableTxId): Boolean {
        val status = repository.getStatus(id).getOrNull()

        if (status?.isLive == false) {
            Timber.d("tx=${id.value} recovery-skipped reason=decided status=$status")

            return false
        }

        return true
    }

    private suspend fun follow(id: DurableTxId, extrinsic: SendableExtrinsic) {
        val chain = chainRegistry.getChain(chainProvider.chainId())

        // Held for as long as the watch lives. This subscription is the only thing following the
        // transaction while it is in flight, and a connection torn down because the app went to background
        // would end it — handing it to the recovery pass to decide the slow way, over its whole window.
        chainConnectionRefCounter.withConnectionEnabled(chain.id, CONNECTION_LABEL) {
            watchSubmission(id, chain, extrinsic)
        }
    }

    private suspend fun watchSubmission(
        id: DurableTxId,
        chain: Chain,
        extrinsic: SendableExtrinsic,
    ) = coroutineScope {
        // Only a post-pool invalidation is worth resubmitting: anything else releases the transaction to
        // the recovery pass, which is the one place allowed to decide it.
        val recoveryStrategy = resubmitWhenValidFactory.createForTxInvalidation(chain.id, RECOVERY_MAX_ATTEMPTS)
        val events = Channel<ExtrinsicStatus>(Channel.BUFFERED)

        val pump = launch {
            runCatching {
                extrinsicService.submitAndWatchBuiltExtrinsic(chain, extrinsic, recoveryStrategy)
                    .collect(events::send)
            }
            events.close()
        }

        try {
            while (true) {
                // A dead subscription is handled as the watcher releasing rather than as someone else
                // invalidating it. The timeout always fires long before the extrinsic can no longer execute.
                val status = withTimeoutOrNull(SILENCE_TIMEOUT) {
                    events.receiveCatching().getOrNull()
                }

                if (status == null) {
                    Timber.w("tx=${id.value} submission-abandoned reason=silence-timeout")
                    break
                }

                if (handle(id, status)) break
            }
        } finally {
            pump.cancel()
        }
    }

    /** Returns true when the transaction is done being watched. */
    private suspend fun handle(id: DurableTxId, status: ExtrinsicStatus): Boolean = when (status) {
        // Pre-inclusion states carry no evidence either way. They must not lower a transaction that has
        // some: the resubmission path keeps consuming this flow after an inclusion, so one of these can
        // arrive behind an InBlock, and clearing the record there would withdraw its effects on nothing.
        is ExtrinsicStatus.Future,
        is ExtrinsicStatus.Ready,
        is ExtrinsicStatus.Broadcast -> false

        // Not finalized, so a terminal verdict must not rest on it: a proven failure here proposes nothing
        // and the transaction stays PENDING for the pass to decide.
        is ExtrinsicStatus.InBlock -> {
            val at = blockOf(status.blockHash)
            val outcome = at?.let { dispatchOutcome(status.blockHash, id) }

            if (outcome == ExtrinsicOutcome.SUCCESS) {
                propose(id, Verdict(DurableTxStatus.PENDING_SUCCESS, successDetectedAt = at))
            }
            false
        }

        is ExtrinsicStatus.Retracted -> {
            clearRecordIfItNames(id, status.blockHash)
            false
        }

        is ExtrinsicStatus.Finalized -> {
            when (dispatchOutcome(status.blockHash, id)) {
                ExtrinsicOutcome.SUCCESS ->
                    propose(id, Verdict(DurableTxStatus.FINALIZED_SUCCESS, blockOf(status.blockHash)))

                ExtrinsicOutcome.FAILURE ->
                    propose(id, Verdict(DurableTxStatus.FAILURE, successDetectedAt = null))

                null -> Unit
            }
            true
        }

        // Recovery has already had its chance to resubmit by the time these surface as terminal.
        is ExtrinsicStatus.Dropped,
        is ExtrinsicStatus.Invalid,
        is ExtrinsicStatus.Usurped -> true

        is ExtrinsicStatus.FailedToSubmit -> {
            // Validation runs before the extrinsic is handed to a node and a rejection there is only ever
            // propagated when recovery declined to resubmit, so nothing can ever include these bytes:
            // finalized-grade evidence without waiting for finality.
            if (status.exception is PreSubmissionValidationFailed) {
                propose(id, Verdict(DurableTxStatus.FAILURE, successDetectedAt = null))
            }
            true
        }

        is ExtrinsicStatus.Other -> false
    }

    /**
     * The record is cleared only when it names the block that was retracted; the status is lowered with it,
     * because leaving PENDING_SUCCESS behind with no evidence would keep its effects trusted for a whole
     * mortality window on the strength of a block that no longer exists.
     */
    private suspend fun clearRecordIfItNames(id: DurableTxId, blockHash: String) {
        val facts = repository.getFacts(id).getOrNull() ?: return
        if (facts.successDetectedAt?.blockHash != blockHash) return

        propose(id, Verdict(DurableTxStatus.PENDING, successDetectedAt = null))
    }

    /**
     * A terminal row is never rewritten, so a late event cannot un-fail a failed transaction; the
     * compare-and-set then covers a status that moved since it was read.
     */
    private suspend fun propose(id: DurableTxId, verdict: Verdict) {
        val observed = repository.getStatus(id).getOrNull() ?: run {
            Timber.w("tx=${id.value} proposal-skipped to=${verdict.status} reason=status-unreadable")

            return
        }

        if (!observed.isLive) {
            Timber.d("tx=${id.value} proposal-skipped to=${verdict.status} reason=not-live observed=$observed")

            return
        }

        repository.compareAndSetStatus(id, observed, verdict)
            .onFailure { Timber.w(it, "tx=${id.value} proposal-write-failed to=${verdict.status}") }
    }

    private suspend fun dispatchOutcome(blockHash: String, id: DurableTxId): ExtrinsicOutcome? {
        val facts = repository.getFacts(id).getOrNull() ?: return null
        val view = chainViewFactory.pin().getOrNull() ?: return null

        return view.dispatchOutcomeAt(blockHash, facts.txHash).getOrNull()
    }

    private suspend fun blockOf(blockHash: String): CheckpointBlock? {
        val view = chainViewFactory.pin().getOrNull() ?: return null
        val number = view.blockNumberAt(blockHash).getOrNull() ?: return null

        return CheckpointBlock(number, blockHash)
    }

    private companion object {
        const val CONNECTION_LABEL = "DurableSubmission"

        val SILENCE_TIMEOUT = 30.seconds

        /** Mirrors the platform default; the constant itself lives in a module this cannot depend on. */
        const val RECOVERY_MAX_ATTEMPTS = 3
    }
}
