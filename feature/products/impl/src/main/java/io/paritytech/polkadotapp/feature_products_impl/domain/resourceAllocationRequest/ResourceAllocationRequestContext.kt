package io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest

import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocationOutcome
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.OnExistingAllowancePolicy
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hand-off between the caller that asked for resources and the prompt showing the request.
 *
 * The prompt approves by running [approve]'s `allocate` lambda, but whether that lambda actually
 * performs anything is the caller's business: the native context runs it and delivers its
 * outcomes, while the TrUAPI one answers yes without running it, because the core owns the
 * allocation.
 */
interface ResourceAllocationRequestContext {
    val productId: ProductId
    val resources: List<ApAllocatableResource>
    val onExisting: OnExistingAllowancePolicy

    suspend fun approve(allocate: suspend () -> Result<List<ApAllocationOutcome>>)

    suspend fun reject()

    /**
     * The sheet went away without an answer. Runs during `onCleared`, so it cannot suspend, and it
     * must be safe to call after a decision was already delivered.
     */
    fun onAbandoned()
}

/**
 * The native path: the prompt owns the allocation, so the outcomes — not just the user's answer —
 * travel back through here.
 */
class NativeResourceAllocationRequestContext(
    override val productId: ProductId,
    override val resources: List<ApAllocatableResource>,
    override val onExisting: OnExistingAllowancePolicy,
) : ResourceAllocationRequestContext {
    private val outcomes = CompletableDeferred<List<ApAllocationOutcome>>()

    override suspend fun approve(allocate: suspend () -> Result<List<ApAllocationOutcome>>) {
        allocate()
            .onSuccess { outcomes.complete(it) }
            // The caller is waiting on this prompt alone, so an unexpected failure still has to answer it
            .onFailure { completeAll(ApAllocationOutcome.NotAvailable) }
    }

    override suspend fun reject() {
        completeAll(ApAllocationOutcome.Rejected)
    }

    override fun onAbandoned() {
        completeAll(ApAllocationOutcome.NotAvailable)
    }

    private fun completeAll(outcome: ApAllocationOutcome) {
        outcomes.complete(List(resources.size) { outcome })
    }

    suspend fun awaitOutcomes(): List<ApAllocationOutcome> = outcomes.await()
}

@Singleton
class ResourceAllocationRequestContextHolder @Inject constructor() {
    private var context: ResourceAllocationRequestContext? = null

    fun set(context: ResourceAllocationRequestContext) {
        this.context = context
    }

    fun get(): ResourceAllocationRequestContext? = context

    /**
     * Clears the holder only while it still belongs to [owner]. The prompt's ViewModel is cleared
     * after its dismiss animation, by which time the holder may already carry the next request's
     * context.
     */
    fun clear(owner: ResourceAllocationRequestContext) {
        if (context === owner) {
            context = null
        }
    }
}
