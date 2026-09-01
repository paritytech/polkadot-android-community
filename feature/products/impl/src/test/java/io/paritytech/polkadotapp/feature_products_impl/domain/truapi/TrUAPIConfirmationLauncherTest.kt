package io.paritytech.polkadotapp.feature_products_impl.domain.truapi

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.signing.RawPayloadContent
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContextHolder
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRawPayload
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody
import io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof.CrossProductProofRequester
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.ProductPermissionGuard
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest.ResourceAllocationRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class TrUAPIConfirmationLauncherTest {
    private val caller = ProductId.fromStoredValue("caller.dot")
    private val target = ProductId.fromStoredValue("target.dot")
    private val suffix = DerivationIndex32.fromUInt(7u)
    private val message = byteArrayOf(1, 2).toDataByteArray()

    private val signingContextHolder = SigningContextHolder()
    private val resourceAllocationContextHolder = ResourceAllocationRequestContextHolder()
    private val permissionGuard: ProductPermissionGuard = mock()
    private val crossProductProofRequester = FakeCrossProductProofRequester()
    private val productsRouter: ProductsRouter = mock()

    private val launcher = TrUAPIConfirmationLauncher(
        signingContextHolder = signingContextHolder,
        resourceAllocationContextHolder = resourceAllocationContextHolder,
        permissionGuard = permissionGuard,
        crossProductProofRequester = crossProductProofRequester,
        productsRouter = productsRouter,
    )

    // One table rather than a test per variant: the review-to-permission mapping
    // is the claim under test, and a wrong entry is the failure that matters.
    @Test
    fun `permission-gated reviews ask for the permission their native call would`() = runBlocking<Unit> {
        val cases = listOf(
            TrUAPIConfirmation.AccountAccess(caller, target) to ProductPermission.AccountAccess("target.dot"),
            TrUAPIConfirmation.StatementSign(caller, target) to ProductPermission.AccountAccess("target.dot"),
            TrUAPIConfirmation.AccountAlias(caller, target) to ProductPermission.AccountAccess("target.dot"),
            TrUAPIConfirmation.IdentityDisclosure(caller) to ProductPermission.UserIdentityAccess,
        )

        cases.forEach { (confirmation, permission) ->
            val case = confirmation::class.simpleName

            clearInvocations(permissionGuard)
            withPermissionAnswer(true)
            assertTrue(case, launcher.awaitDecision(confirmation))
            verify(permissionGuard).requestPermission(caller, permission)

            withPermissionAnswer(false)
            assertFalse(case, launcher.awaitDecision(confirmation))
        }
    }

    @Test
    fun `preimage submit consumes the preimage-submit permission`() = runBlocking<Unit> {
        whenever(permissionGuard.consumePermission(any(), any())).thenReturn(true)

        val approved = launcher.awaitDecision(TrUAPIConfirmation.PreimageSubmit(caller))

        assertTrue(approved)
        verify(permissionGuard).consumePermission(caller, ProductPermission.RemotePermission.PreimageSubmitAccess)
    }

    @Test
    fun `create proof goes to the cross-product proof prompt`() = runBlocking<Unit> {
        val confirmation = TrUAPIConfirmation.CreateProof(caller, target, suffix, message)
        crossProductProofRequester.answer = true

        assertTrue(launcher.awaitDecision(confirmation))
        assertEquals(caller, crossProductProofRequester.callingProduct)
        assertEquals(target, crossProductProofRequester.onBehalfOf)
        assertEquals(suffix, crossProductProofRequester.suffix)
        assertEquals(message, crossProductProofRequester.message)

        crossProductProofRequester.answer = false
        assertFalse(launcher.awaitDecision(confirmation))
    }

    @Test
    fun `signing goes to the app's signing sheet and never signs locally`() = runBlocking<Unit> {
        val decision = async { launcher.awaitDecision(signingConfirmation()) }
        yield()

        verify(productsRouter).openSignTransaction()
        val context = signingContextHolder.get() as TrUAPISigningContext
        context.approve { error("the core signs, not the app") }
        context.onAbandoned()

        assertTrue(decision.await())
    }

    @Test
    fun `the launcher stays busy until the signing sheet is dismissed`() = runBlocking<Unit> {
        val decision = async { launcher.awaitDecision(signingConfirmation()) }
        yield()
        val context = signingContextHolder.get() as TrUAPISigningContext
        context.deliverRejection()
        yield()

        assertFalse(decision.isCompleted)

        context.onAbandoned()
        assertFalse(decision.await())
    }

    @Test
    fun `resource allocation opens the native allocation sheet confirm-only`() = runBlocking<Unit> {
        val confirmation = TrUAPIConfirmation.ResourceAllocation(caller, listOf(ApAllocatableResource.AutoSigning))
        val decision = async { launcher.awaitDecision(confirmation) }
        yield()

        verify(productsRouter).openResourceAllocationRequestPrompt()
        val context = resourceAllocationContextHolder.get() as TrUAPIResourceAllocationContext
        assertEquals(caller, context.productId)
        assertEquals(listOf<ApAllocatableResource>(ApAllocatableResource.AutoSigning), context.resources)
        context.approve { error("the core allocates, not the app") }
        context.onAbandoned()

        assertTrue(decision.await())
    }

    @Test
    fun `a rejected resource allocation is a refusal`() = runBlocking<Unit> {
        val confirmation = TrUAPIConfirmation.ResourceAllocation(caller, listOf(ApAllocatableResource.AutoSigning))
        val decision = async { launcher.awaitDecision(confirmation) }
        yield()

        val context = resourceAllocationContextHolder.get() as TrUAPIResourceAllocationContext
        context.reject()
        context.onAbandoned()

        assertFalse(decision.await())
    }

    @Test
    fun `an abandoned signing sheet is a refusal and frees the launcher`() = runBlocking<Unit> {
        val first = async { launcher.awaitDecision(signingConfirmation()) }
        yield()
        (signingContextHolder.get() as TrUAPISigningContext).onAbandoned()

        assertFalse(first.await())
        withPermissionAnswer(true)
        assertTrue(launcher.awaitDecision(TrUAPIConfirmation.AccountAccess(caller, target)))
    }

    @Test
    fun `a sheet-backed confirmation waits until the previous sheet is dismissed`() = runBlocking<Unit> {
        val confirmation = TrUAPIConfirmation.ResourceAllocation(caller, listOf(ApAllocatableResource.AutoSigning))
        val first = async { launcher.awaitDecision(confirmation) }
        yield()
        val firstContext = resourceAllocationContextHolder.get() as TrUAPIResourceAllocationContext
        firstContext.approve { error("confirm-only") }
        val second = async { launcher.awaitDecision(confirmation) }
        yield()

        assertSame(firstContext, resourceAllocationContextHolder.get())

        firstContext.onAbandoned()
        assertTrue(first.await())
        yield()
        val secondContext = resourceAllocationContextHolder.get() as TrUAPIResourceAllocationContext
        assertNotSame(firstContext, secondContext)
        secondContext.reject()
        secondContext.onAbandoned()

        assertFalse(second.await())
    }

    @Test
    fun `an already-granted permission does not wait behind an open sheet`() = runBlocking<Unit> {
        withPermissionAnswer(true)
        val signing = async { launcher.awaitDecision(signingConfirmation()) }
        yield()

        val approved = launcher.awaitDecision(TrUAPIConfirmation.AccountAccess(caller, target))

        assertTrue(approved)
        assertFalse(signing.isCompleted)
        (signingContextHolder.get() as TrUAPISigningContext).onAbandoned()
        signing.await()
    }

    @Test
    fun `an empty resource allocation is approved without a sheet`() = runBlocking<Unit> {
        val approved = launcher.awaitDecision(TrUAPIConfirmation.ResourceAllocation(caller, emptyList()))

        assertTrue(approved)
        verify(productsRouter, never()).openResourceAllocationRequestPrompt()
    }

    private suspend fun withPermissionAnswer(granted: Boolean) {
        whenever(permissionGuard.requestPermission(any(), any())).thenReturn(granted)
    }

    private fun signingConfirmation() = TrUAPIConfirmation.Signing(
        requesterProductId = caller.value,
        request = SigningRequestBody.Raw(
            SigningRawPayload(
                account = ProductAccountId(caller.value, DerivationIndex32.fromUInt(0u)),
                type = RawPayloadContent.Bytes(byteArrayOf(1)),
            ),
        ),
    )
}

private class FakeCrossProductProofRequester : CrossProductProofRequester {
    var answer = true
    var callingProduct: ProductId? = null
    var onBehalfOf: ProductId? = null
    var suffix: DerivationIndex32? = null
    var message: DataByteArray? = null

    override suspend fun awaitApproval(
        callingProduct: ProductId,
        onBehalfOf: ProductId,
        suffix: DerivationIndex32,
        message: DataByteArray,
    ): Boolean {
        this.callingProduct = callingProduct
        this.onBehalfOf = onBehalfOf
        this.suffix = suffix
        this.message = message
        return answer
    }
}
