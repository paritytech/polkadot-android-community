package io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof

import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class RealCrossProductProofRequesterTest {
    private val caller = ProductId.fromStoredValue("caller.dot")
    private val owner = ProductId.fromStoredValue("owner.dot")
    private val suffix = DerivationIndex32.fromUInt(1u)
    private val message = byteArrayOf(7).toDataByteArray()

    private val holder = CrossProductProofContextHolder()
    private val productsRouter: ProductsRouter = mock()

    private val requester = RealCrossProductProofRequester(holder, productsRouter)

    @Test
    fun `a product acting under its own context is approved without a prompt`() = runBlocking<Unit> {
        val approved = requester.awaitApproval(caller, caller, suffix, message)

        assertTrue(approved)
        verify(productsRouter, never()).openCrossProductProofPrompt()
    }

    @Test
    fun `approval travels back once the prompt is dismissed`() = runBlocking<Unit> {
        val decision = async { requester.awaitApproval(caller, owner, suffix, message) }
        yield()

        verify(productsRouter).openCrossProductProofPrompt()
        val context = requireNotNull(holder.get())
        context.deliverApproved()
        yield()
        assertFalse(decision.isCompleted)

        context.onAbandoned()

        assertTrue(decision.await())
    }

    @Test
    fun `rejection travels back`() = runBlocking<Unit> {
        val decision = async { requester.awaitApproval(caller, owner, suffix, message) }
        yield()

        val context = requireNotNull(holder.get())
        context.deliverRejected()
        context.onAbandoned()

        assertFalse(decision.await())
    }

    @Test
    fun `an abandoned prompt fails closed`() = runBlocking<Unit> {
        val decision = async { requester.awaitApproval(caller, owner, suffix, message) }
        yield()

        requireNotNull(holder.get()).onAbandoned()

        assertFalse(decision.await())
    }

    @Test
    fun `clear only releases the holder for its owner`() {
        val first = CrossProductProofContext(caller, owner, suffix, message)
        val second = CrossProductProofContext(caller, owner, suffix, message)
        holder.set(first)
        holder.set(second)

        holder.clear(first)

        assertSame(second, holder.get())
    }
}
