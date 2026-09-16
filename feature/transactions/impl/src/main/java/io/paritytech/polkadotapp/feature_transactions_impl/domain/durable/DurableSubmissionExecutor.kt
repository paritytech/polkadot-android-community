package io.paritytech.polkadotapp.feature_transactions_impl.domain.durable

import dagger.Lazy
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.AsyncDurableSubmissionPolicy
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.OperationGroupId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPreparation
import io.paritytech.polkadotapp.feature_transactions_impl.data.durable.DurableTxRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Builds every transaction waiting for its policy, in this process, as soon as it is committed.
 *
 * It reacts to the ledger rather than to callers: a scheduled transaction is only acted on once a read of the
 * ledger shows it, so one scheduled inside a transaction that has not committed yet is simply not seen until
 * it has. Waiting transactions are grouped by policy and then by operation group, and each group is worked in
 * its own coroutine — a policy waiting hours for a coin to appear never holds up another group.
 *
 * Staying alive in the background is not this class's job: while anything waits, it keeps the recovery worker
 * scheduled, and the worker's foreground promotion is what keeps this process running.
 */
@Singleton
class DurableSubmissionExecutor @Inject constructor(
    private val repository: DurableTxRepository,
    // Lazy: a domain's policy may depend on services that sit above the engine.
    private val policies: Lazy<Map<String, @JvmSuppressWildcards AsyncDurableSubmissionPolicy>>,
    private val launcher: DurableSubmissionLauncher,
    private val recoveryScheduler: DurableRecoveryScheduler,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
    dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.computation)

    private val mutex = Mutex()
    private var collector: Job? = null
    private val buckets = mutableMapOf<Bucket, Job>()

    /** Groups that gained a waiting transaction while their coroutine was already running. */
    private val revisited = mutableSetOf<Bucket>()

    /** Idempotent. */
    fun ensureStarted() {
        scope.launch {
            mutex.withLock {
                if (collector?.isActive == true) return@launch

                collector = scope.launch { collectPendingSubmissions() }
            }
        }
    }

    /** Cancels everything this instance runs. */
    fun close() {
        scope.cancel()
    }

    private suspend fun collectPendingSubmissions() {
        repository.subscribePendingSubmissions()
            .map { pending -> pending.mapTo(mutableSetOf()) { it.id to it.bucket() } }
            .distinctUntilChanged()
            .collect { pending ->
                if (pending.isEmpty()) return@collect

                durabilityLogD("pending-submissions count=${pending.size}")

                recoveryScheduler.ensureRunning()

                pending.mapTo(mutableSetOf()) { it.second }.forEach { launchIfIdle(it) }
            }
    }

    private suspend fun launchIfIdle(bucket: Bucket) = mutex.withLock {
        if (buckets[bucket]?.isActive == true) {
            revisited += bucket

            return@withLock
        }

        buckets[bucket] = scope.launch { runBucket(bucket) }
    }

    private suspend fun runBucket(bucket: Bucket) {
        val policy = policies.get()[bucket.policyId]

        if (policy == null) {
            durabilityLogE("${bucket.logId()} submission-skipped reason=no-registered-policy")
            abandonAll(bucket)
            finishBucket(bucket)

            return
        }

        chainConnectionRefCounter.withConnectionEnabled(policy.chainId, CONNECTION_LABEL) {
            var failures = 0

            do {
                val decided = workOnce(bucket, policy)

                if (decided) {
                    failures = 0
                } else {
                    val backoff = backoffAfter(++failures)
                    durabilityLogW("${bucket.logId()} submission-backoff failures=$failures delay=$backoff")
                    delay(backoff)
                }
            } while (!finishBucket(bucket))
        }
    }

    /** Returns whether anything was decided, which is what separates progress from a loop worth backing off. */
    private suspend fun workOnce(bucket: Bucket, policy: AsyncDurableSubmissionPolicy): Boolean {
        val transactions = repository.getPendingSubmissions(bucket.policyId, bucket.groupId).getOrElse {
            durabilityLogW("${bucket.logId()} pending-submissions-read-failed error=$it")

            return false
        }

        if (transactions.isEmpty()) return true

        durabilityLogD("${bucket.logId()} preparing transactions=${transactions.map { it.id.value }}")

        val outcomes = try {
            policy.prepareSubmission(transactions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }.getOrElse {
            durabilityLogW("${bucket.logId()} prepare-failed error=$it")

            return false
        }

        outcomes.filterKeys { id -> transactions.any { it.id == id } }
            .forEach { (id, outcome) -> apply(bucket, id, outcome) }

        return outcomes.isNotEmpty()
    }

    private suspend fun apply(bucket: Bucket, id: DurableTxId, outcome: SubmissionPreparation) {
        when (outcome) {
            is SubmissionPreparation.Ready -> launcher.startAttempt(id, outcome.extrinsic)
                .onFailure {
                    // An extrinsic the engine cannot track would be rejected the same way on every rebuild.
                    durabilityLogE("${bucket.logId()} entry=${id.value} attempt-rejected error=$it")
                    repository.abandonSubmission(id)
                }

            SubmissionPreparation.GiveUp -> {
                durabilityLogI("${bucket.logId()} entry=${id.value} policy-gave-up")
                repository.abandonSubmission(id)
            }
        }
    }

    private suspend fun abandonAll(bucket: Bucket) {
        repository.getPendingSubmissions(bucket.policyId, bucket.groupId)
            .getOrNull()
            .orEmpty()
            .forEach { repository.abandonSubmission(it.id) }
    }

    /**
     * Ends the bucket's coroutine unless something arrived for it meanwhile. Decided under the same lock
     * [launchIfIdle] takes, so a transaction committed just as the coroutine was finding nothing to do is never
     * left waiting for another ledger change to notice it.
     */
    private suspend fun finishBucket(bucket: Bucket): Boolean = mutex.withLock {
        if (revisited.remove(bucket)) return@withLock false

        val stillPending = repository.getPendingSubmissions(bucket.policyId, bucket.groupId)
            .getOrNull()
            ?.isNotEmpty()
            ?: true

        if (stillPending) return@withLock false

        buckets -= bucket

        true
    }

    private fun backoffAfter(failures: Int): Duration =
        (INITIAL_BACKOFF * (1 shl (failures - 1).coerceAtMost(MAX_BACKOFF_DOUBLINGS))).coerceAtMost(MAX_BACKOFF)

    private data class Bucket(val policyId: String, val groupId: OperationGroupId?) {
        fun logId() = "policy=$policyId group=${groupId?.value}"
    }

    private fun ScheduledDurableTx.bucket() = Bucket(policy.id, groupId)

    private companion object {
        const val CONNECTION_LABEL = "DurableSubmissionExecutor"

        val INITIAL_BACKOFF = 2.seconds
        val MAX_BACKOFF = 2.minutes
        const val MAX_BACKOFF_DOUBLINGS = 10
    }
}
