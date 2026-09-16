package io.paritytech.polkadotapp.feature_transactions_impl.domain.durable

import dagger.Lazy
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.AsyncDurableSubmissionPolicy
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxEntry
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.Verdict
import io.paritytech.polkadotapp.feature_transactions_impl.data.durable.DurableTxRepository
import javax.inject.Inject

/**
 * Writes a verdict about one submitted attempt, for both the recovery pass and the submission watch.
 *
 * A failure is where the two would otherwise diverge: a transaction whose policy still wants it goes back to
 * [DurableTxStatus.PENDING_SUBMISSION] instead, keeping whatever its domain locked. Doing that here, rather
 * than in each writer, is what keeps a failure from ever becoming terminal through the path that forgot.
 */
class DurableVerdictWriter @Inject constructor(
    private val repository: DurableTxRepository,
    // Lazy: a domain's policy may depend on services that sit above the engine.
    private val policies: Lazy<Map<String, @JvmSuppressWildcards AsyncDurableSubmissionPolicy>>,
) {
    /** Writes only while [observed] is still the transaction's status and attempt. Returns whether it wrote. */
    suspend fun write(observed: DurableTxEntry, verdict: Verdict): Result<Boolean> {
        val effective = if (verdict.status == DurableTxStatus.FAILURE) {
            retryInstead(observed).getOrElse { return Result.failure(it) } ?: verdict
        } else {
            verdict
        }

        return repository.compareAndSetStatus(observed.id, observed.status, observed.txHash, effective)
    }

    /**
     * A policy that cannot be read fails the write instead of the transaction: the verdict is re-derived on
     * the next pass, while a failure written now could never be taken back.
     */
    private suspend fun retryInstead(entry: DurableTxEntry): Result<Verdict?> =
        repository.getSubmissionPolicy(entry.id).mapCatching { reference ->
            val policy = reference?.let { policies.get()[it.id] }

            when {
                reference == null -> null

                policy == null -> {
                    durabilityLogE("${entry.logId()} retry-skipped reason=no-registered-policy policy=${reference.id}")
                    null
                }

                policy.canRetry(entry, reference.params) -> {
                    durabilityLogI("${entry.logId()} failure-deferred-to-policy policy=${reference.id}")
                    Verdict(DurableTxStatus.PENDING_SUBMISSION, successDetectedAt = null)
                }

                else -> {
                    durabilityLogI("${entry.logId()} retry-declined policy=${reference.id}")
                    null
                }
            }
        }
}
