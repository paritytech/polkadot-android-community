package io.paritytech.polkadotapp.feature_transaction_storage_api.domain.slotAllocator

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector

interface TransactionStorageSlotAllocator {
    /**
     * Reports its progress into [diagnostics]; callers with no UI attached pass
     * [StalenessReportCollector.NoOp].
     */
    context(diagnostics: StalenessReportCollector)
    suspend fun allocate(target: AccountId, strategy: OnExistingAllocationStrategy): Result<Unit>
}

sealed class TransactionStorageSlotAllocationError(cause: Throwable?) : Throwable(cause) {
    class NoAllocationAvailable(cause: Throwable) : TransactionStorageSlotAllocationError(cause)
    class Unknown(cause: Throwable) : TransactionStorageSlotAllocationError(cause)
}
