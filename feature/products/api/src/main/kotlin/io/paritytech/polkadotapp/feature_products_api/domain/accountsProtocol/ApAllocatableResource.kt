package io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol

import io.paritytech.polkadotapp.feature_products_api.model.ProductDerivationIndex

sealed interface ApAllocatableResource {
    data object StatementStoreAllowance : ApAllocatableResource

    data object BulletInAllowance : ApAllocatableResource

    data class SmartContractAllowance(val dest: ProductDerivationIndex) : ApAllocatableResource

    data object AutoSigning : ApAllocatableResource
}

sealed interface ApAllocationOutcome {
    data class Allocated(val resource: ApAllocatedResource) : ApAllocationOutcome

    data object Rejected : ApAllocationOutcome

    data object NotAvailable : ApAllocationOutcome
}

sealed interface ApAllocatedResource {
    data class StatementStoreAllowance(val slotAccountKey: SlotAccountKey) : ApAllocatedResource

    data class BulletInAllowance(val slotAccountKey: SlotAccountKey) : ApAllocatedResource

    data object SmartContractAllowance : ApAllocatedResource
}

enum class OnExistingAllowancePolicy {
    IGNORE,
    INCREASE,
}
