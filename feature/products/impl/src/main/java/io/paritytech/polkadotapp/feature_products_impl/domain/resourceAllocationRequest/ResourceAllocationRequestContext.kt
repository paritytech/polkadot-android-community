package io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest

import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

class ResourceAllocationRequestContext(
    val productId: ProductId,
    val resources: List<ApAllocatableResource>,
) {
    sealed interface Decision {
        data object Approved : Decision
        data object Rejected : Decision
    }

    private val decision = CompletableDeferred<Decision>()

    fun deliverApproved() {
        decision.complete(Decision.Approved)
    }

    fun deliverRejected() {
        decision.complete(Decision.Rejected)
    }

    suspend fun awaitDecision(): Decision = decision.await()
}

@Singleton
class ResourceAllocationRequestContextHolder @Inject constructor() {
    private var context: ResourceAllocationRequestContext? = null

    fun set(context: ResourceAllocationRequestContext) {
        this.context = context
    }

    fun get(): ResourceAllocationRequestContext? = context

    fun clear() {
        context = null
    }
}
