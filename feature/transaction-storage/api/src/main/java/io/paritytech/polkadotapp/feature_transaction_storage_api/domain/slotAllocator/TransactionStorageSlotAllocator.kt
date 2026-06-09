package io.paritytech.polkadotapp.feature_transaction_storage_api.domain.slotAllocator

import io.paritytech.polkadotapp.common.domain.model.AccountId

interface TransactionStorageSlotAllocator {
    suspend fun allocate(target: AccountId, strategy: OnExistingAllocationStrategy): Result<Unit>
}

sealed class TransactionStorageSlotAllocationError(cause: Throwable?) : Throwable(cause) {
    class NoAllocationAvailable(cause: Throwable) : TransactionStorageSlotAllocationError(cause)
    class Unknown(cause: Throwable) : TransactionStorageSlotAllocationError(cause)
}
