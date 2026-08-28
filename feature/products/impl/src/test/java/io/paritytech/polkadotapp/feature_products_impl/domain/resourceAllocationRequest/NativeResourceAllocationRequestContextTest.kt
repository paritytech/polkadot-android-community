package io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest

import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatedResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocationOutcome
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.OnExistingAllowancePolicy
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeResourceAllocationRequestContextTest {
    private val product = ProductId.fromStoredValue("acme.dot")
    private val resources = listOf(
        ApAllocatableResource.StatementStoreAllowance,
        ApAllocatableResource.AutoSigning,
    )

    private val context = NativeResourceAllocationRequestContext(
        productId = product,
        resources = resources,
        onExisting = OnExistingAllowancePolicy.INCREASE,
    )

    @Test
    fun `approve delivers what the allocation produced`() = runBlocking<Unit> {
        val produced = listOf(
            ApAllocationOutcome.Allocated(ApAllocatedResource.SmartContractAllowance),
            ApAllocationOutcome.Rejected,
        )

        context.approve { Result.success(produced) }

        assertEquals(produced, context.awaitOutcomes())
    }

    @Test
    fun `a failed allocation still answers every resource`() = runBlocking<Unit> {
        context.approve { Result.failure(IllegalStateException("boom")) }

        assertEquals(
            listOf(ApAllocationOutcome.NotAvailable, ApAllocationOutcome.NotAvailable),
            context.awaitOutcomes(),
        )
    }

    @Test
    fun `reject answers every resource`() = runBlocking<Unit> {
        context.reject()

        assertEquals(
            listOf(ApAllocationOutcome.Rejected, ApAllocationOutcome.Rejected),
            context.awaitOutcomes(),
        )
    }

    @Test
    fun `an abandoned prompt answers rather than stranding the caller`() = runBlocking<Unit> {
        context.onAbandoned()

        assertEquals(
            listOf(ApAllocationOutcome.NotAvailable, ApAllocationOutcome.NotAvailable),
            context.awaitOutcomes(),
        )
    }

    @Test
    fun `the first answer wins`() = runBlocking<Unit> {
        context.reject()
        context.onAbandoned()

        assertEquals(
            listOf(ApAllocationOutcome.Rejected, ApAllocationOutcome.Rejected),
            context.awaitOutcomes(),
        )
    }
}
