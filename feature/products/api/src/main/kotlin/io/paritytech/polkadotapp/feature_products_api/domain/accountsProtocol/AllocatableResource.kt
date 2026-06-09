package io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol

import io.paritytech.polkadotapp.feature_products_api.model.ProductDerivationIndex

sealed interface AllocatableResource {
    data object StatementStoreAllowance : AllocatableResource

    data object BulletInAllowance : AllocatableResource

    data class SmartContractAllowance(val dest: ProductDerivationIndex) : AllocatableResource

    data object AutoSigning : AllocatableResource
}

sealed interface AllocationOutcome {
    data object Allocated : AllocationOutcome

    data object Rejected : AllocationOutcome

    data object NotAvailable : AllocationOutcome
}
