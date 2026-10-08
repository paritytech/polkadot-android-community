package io.paritytech.polkadotapp.feature_chats_impl.domain.sessions.signer

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.OnExistingAllocationStrategy
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.SlotPriority
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlotAllocator
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlots

// mockk cannot stub `allocate`: its context parameter breaks mockk's reflection over the whole interface.
class RecordingSlotAllocator(
    var hasCurrentAllocation: Boolean = false,
    var allocationResult: Result<Unit> = Result.success(Unit),
) : StatementStoreSlotAllocator {
    class AllocateCall(val target: AccountId, val strategy: OnExistingAllocationStrategy, val priority: SlotPriority)

    val allocateCalls = mutableListOf<AllocateCall>()
    val deallocatedTargets = mutableListOf<AccountId>()

    context(diagnostics: StalenessReportCollector)
    override suspend fun allocate(
        target: AccountId,
        strategy: OnExistingAllocationStrategy,
        priority: SlotPriority,
    ): Result<Unit> {
        allocateCalls += AllocateCall(target, strategy, priority)
        return allocationResult
    }

    override suspend fun deallocateAllSlots(target: AccountId): Result<Unit> {
        deallocatedTargets += target
        return Result.success(Unit)
    }

    override suspend fun hasCurrentAllocation(target: AccountId): Result<Boolean> = Result.success(hasCurrentAllocation)

    override suspend fun allocationsFor(target: AccountId): Result<StatementStoreSlots> = error("Not used by chat signer tests")

    override suspend fun scheduleSlotRenewals(): Result<Unit> = error("Not used by chat signer tests")
}
