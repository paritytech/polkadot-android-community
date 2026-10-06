package io.paritytech.polkadotapp.feature_statement_store_api.domain.notificationAllocator

import io.paritytech.polkadotapp.common.domain.model.AccountId
import kotlin.time.Duration

/**
 * Funds statement accounts from anonymous notification slots: each binding lets its account keep one statement of
 * up to 10 KiB until the current period ends plus the runtime's grace window. An account can hold one live binding,
 * so callers pass a fresh account per period.
 */
interface NotificationStatementAccountAllocator {
    /** The period slots are claimed in right now; a binding made now lives until this period ends plus grace. */
    fun currentPeriod(): UInt

    /** Whether the connected runtime offers notification slots at all. */
    suspend fun isSupported(): Result<Boolean>

    /**
     * Schedules a durable claim of a notification slot for [target] in the current period. A no-op when a claim
     * for [target] is already in flight or has landed. Returns once the claim is recorded, not when it lands.
     */
    suspend fun allocate(target: AccountId): Result<Unit>

    /**
     * Like [allocate] for each of [targets], in the given order of preference: when fewer slots are free than
     * targets, only the leading ones are scheduled and the rest fail with
     * [NotificationAllocationError.NoFreeSlotInPeriod]. Returns the targets that were scheduled or already claimed.
     */
    suspend fun allocateAll(targets: List<AccountId>): Result<List<AccountId>>

    /**
     * Suspends until a claim for [target] has executed (pending finality is enough), up to [timeout].
     *
     * Fails with [NotificationAllocationError.NoFreeSlotInPeriod] when the claim gave up because no slot was free,
     * and with [NotificationAllocationError.Timeout] when [timeout] elapses first.
     */
    suspend fun awaitAllocated(target: AccountId, timeout: Duration): Result<Unit>
}

/** Allowance one notification binding grants: a single statement of at most this many encoded bytes. */
const val NOTIFICATION_STATEMENT_MAX_SIZE_BYTES: Int = 10 * 1024

sealed class NotificationAllocationError(message: String, cause: Throwable?) : Throwable(message, cause) {
    class NoFreeSlotInPeriod(target: AccountId) :
        NotificationAllocationError("No free notification slot in the current period for $target", null)

    class Timeout(target: AccountId, timeout: Duration) :
        NotificationAllocationError("Notification slot claim for $target did not land within $timeout", null)

    class Unknown(cause: Throwable) : NotificationAllocationError("Notification slot allocation failed", cause)
}
