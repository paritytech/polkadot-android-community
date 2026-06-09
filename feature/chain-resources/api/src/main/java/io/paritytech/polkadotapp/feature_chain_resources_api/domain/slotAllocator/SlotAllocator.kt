package io.paritytech.polkadotapp.feature_chain_resources_api.domain.slotAllocator

import io.paritytech.polkadotapp.common.domain.model.AccountId

enum class OnExistingSlotPolicy {
    /** If any slot is already occupied for this account, fast-return success without allocating. */
    Ignore,

    /** Always allocate another free slot, even if one is already occupied for this account. */
    AllocateAdditionalSlot,
}

interface SlotAllocator {
    suspend fun allocateSlot(
        deviceStatementAccountId: AccountId,
        onExistingSlot: OnExistingSlotPolicy = OnExistingSlotPolicy.Ignore,
    ): Result<Unit>

    suspend fun deallocateAllSlots(deviceStatementAccountId: AccountId): Result<Unit>
}
