package io.paritytech.polkadotapp.feature_products_impl.domain.truapi

import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocationOutcome
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.OnExistingAllowancePolicy
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest.ResourceAllocationRequestContext
import kotlinx.coroutines.CompletableDeferred

/**
 * A core resource-allocation review, presented on the app's own allocation sheet.
 *
 * Confirm-only: the core owns the allocation and performs it after a yes, so
 * the sheet's approval never runs the allocate lambda.
 */
class TrUAPIResourceAllocationContext(
    override val productId: ProductId,
    override val resources: List<ApAllocatableResource>,
) : ResourceAllocationRequestContext {
    // Never consulted: the allocate lambda is not run on this path.
    override val onExisting: OnExistingAllowancePolicy = OnExistingAllowancePolicy.INCREASE

    private val decision = CompletableDeferred<Boolean>()
    private val dismissed = CompletableDeferred<Unit>()

    override suspend fun approve(allocate: suspend () -> Result<List<ApAllocationOutcome>>) {
        decision.complete(true)
    }

    override suspend fun reject() {
        decision.complete(false)
    }

    /** Also the answer for a dismissed sheet, so an abandoned prompt fails closed. */
    override fun onAbandoned() {
        decision.complete(false)
        dismissed.complete(Unit)
    }

    suspend fun await(): Boolean = decision.await()

    suspend fun awaitDismissal() = dismissed.await()
}
