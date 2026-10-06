package io.paritytech.polkadotapp.feature_statement_store_impl.domain.notificationAllocator

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.getRuntime
import io.paritytech.polkadotapp.chains.util.hasCall
import io.paritytech.polkadotapp.chains.util.resources
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flattenResult
import io.paritytech.polkadotapp.common.utils.mapErrorNotInstance
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.SET_NOTIFICATION_STATEMENT_ACCOUNT_CALL
import io.paritytech.polkadotapp.feature_statement_store_api.domain.notificationAllocator.NotificationAllocationError
import io.paritytech.polkadotapp.feature_statement_store_api.domain.notificationAllocator.NotificationStatementAccountAllocator
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.slotAllocator.AllocateContext
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.slotAllocator.AllocateContextResolver
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.slotAllocator.CurrentPeriodProvider
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTransactionService
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration

class RealNotificationStatementAccountAllocator @Inject constructor(
    private val durableTransactionService: DurableTransactionService,
    private val contextResolver: AllocateContextResolver,
    private val seqPicker: NotificationSeqPicker,
    private val allocationLock: NotificationSlotAllocationLock,
    private val currentPeriodProvider: CurrentPeriodProvider,
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
) : NotificationStatementAccountAllocator {
    override fun currentPeriod(): UInt = currentPeriodProvider.current()

    override suspend fun isSupported(): Result<Boolean> = runCancellableCatching {
        chainRegistry.getRuntime(knownChains.people).metadata.resources().hasCall(SET_NOTIFICATION_STATEMENT_ACCOUNT_CALL)
    }

    override suspend fun allocate(target: AccountId): Result<Unit> {
        return allocateAll(listOf(target)).flatMap { allocated ->
            if (target in allocated) Result.success(Unit) else Result.failure(NotificationAllocationError.NoFreeSlotInPeriod(target))
        }
    }

    override suspend fun allocateAll(targets: List<AccountId>): Result<List<AccountId>> {
        return contextResolver.resolve()
            .flatMap { context -> allocationLock.withLock { scheduleWithinCapacity(context, targets) } }
            .onFailure { Timber.e(it, "Notification slot allocation failed for ${targets.size} targets") }
            .mapErrorNotInstance<_, NotificationAllocationError> { NotificationAllocationError.Unknown(it) }
    }

    override suspend fun awaitAllocated(target: AccountId, timeout: Duration): Result<Unit> = runCancellableCatching {
        withTimeoutOrNull(timeout) {
            durableTransactionService.subscribeGroupStates(NOTIFICATION_SLOT_DOMAIN, target.notificationSlotGroup())
                .first { states -> states.anyArrived() || states.allGaveUp() }
        }
    }.flatMap { states -> awaitOutcome(target, timeout, states) }

    private suspend fun scheduleWithinCapacity(context: AllocateContext, targets: List<AccountId>): Result<List<AccountId>> {
        return claimedAmong(targets).flatMap { claimed ->
            seqPicker.freeSlots(context).flatMap { free ->
                val toSchedule = targets.filterNot { it in claimed }.take(free.size)
                scheduleClaims(toSchedule).map { targets.filter { it in claimed || it in toSchedule } }
            }
        }
    }

    private suspend fun claimedAmong(targets: List<AccountId>): Result<Set<AccountId>> {
        return targets
            .map { target -> hasClaim(target).map { claimed -> target.takeIf { claimed } } }
            .flattenResult()
            .map { it.filterNotNull().toSet() }
    }

    private suspend fun hasClaim(target: AccountId): Result<Boolean> {
        return durableTransactionService.getGroupStates(NOTIFICATION_SLOT_DOMAIN, target.notificationSlotGroup())
            .map { states -> states.any { it.status.canArrive } }
    }

    private suspend fun scheduleClaims(targets: List<AccountId>): Result<Unit> {
        return targets.map { target ->
            durableTransactionService.schedule(
                domain = NOTIFICATION_SLOT_DOMAIN,
                groupId = target.notificationSlotGroup(),
                policies = listOf(target.notificationSlotPolicy()),
                onRegister = {},
            ).coerceToUnit()
        }.flattenResult().coerceToUnit()
    }

    private fun awaitOutcome(target: AccountId, timeout: Duration, states: List<DurableTxState>?): Result<Unit> = when {
        states == null -> Result.failure(NotificationAllocationError.Timeout(target, timeout))
        states.anyArrived() -> Result.success(Unit)
        else -> Result.failure(NotificationAllocationError.NoFreeSlotInPeriod(target))
    }

    private fun List<DurableTxState>.anyArrived(): Boolean = any { it.status.isArrived }

    private fun List<DurableTxState>.allGaveUp(): Boolean = isNotEmpty() && none { it.status.canArrive }
}
