package io.paritytech.polkadotapp.feature_chain_resources_impl.data.slotAllocator

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.ResourcesRepository
import io.paritytech.polkadotapp.feature_chain_resources_api.domain.slotAllocator.OnExistingSlotPolicy
import io.paritytech.polkadotapp.feature_chain_resources_api.domain.slotAllocator.SlotAllocator
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.origins.LitePeopleOrigins
import kotlinx.coroutines.delay
import javax.inject.Inject

// TODO: Implement slot allocator
class RealSlotAllocator @Inject constructor(
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
    private val resourcesRepository: ResourcesRepository,
    private val extrinsicService: ExtrinsicService,
    private val litePeopleOrigins: LitePeopleOrigins,
) : SlotAllocator {
    override suspend fun allocateSlot(
        deviceStatementAccountId: AccountId,
        onExistingSlot: OnExistingSlotPolicy,
    ): Result<Unit> {
        delay(2000)
        return Result.success(Unit)
    }

    override suspend fun deallocateAllSlots(deviceStatementAccountId: AccountId): Result<Unit> {
        delay(2000)
        return Result.success(Unit)
    }
}
