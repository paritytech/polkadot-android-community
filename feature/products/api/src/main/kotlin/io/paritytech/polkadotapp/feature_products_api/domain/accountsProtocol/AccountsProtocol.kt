package io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol

import io.paritytech.polkadotapp.feature_products_api.model.ProductId

interface AccountsProtocol {
    /**
     * Returned list matches [resources] in length and order.
     * Single user-authorization round-trip per call.
     */
    suspend fun requestResourceAllocation(
        callingProduct: ProductId,
        resources: List<ApAllocatableResource>,
        onExisting: OnExistingAllowancePolicy,
    ): List<ApAllocationOutcome>
}

suspend fun AccountsProtocol.requestResourceAllocation(
    callingProduct: ProductId,
    resource: ApAllocatableResource,
    onExisting: OnExistingAllowancePolicy,
): ApAllocationOutcome {
    return requestResourceAllocation(callingProduct, listOf(resource), onExisting).single()
}
