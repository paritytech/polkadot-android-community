package io.paritytech.polkadotapp.feature_products_impl.domain.permissions

import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.PermissionDecision
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.mock

class RealProductPermissionRequesterTest {
    private val product = ProductId.fromStoredValue("acme.dot")

    private val holder = PermissionContextHolder()
    private val productsRouter: ProductsRouter = mock()

    private val requester = RealProductPermissionRequester(holder, productsRouter)

    @Test
    fun `a decision travels back once the prompt is dismissed`() = runBlocking<Unit> {
        val decision = async { requester.prompt(product, ProductPermission.UserIdentityAccess) }
        yield()

        val context = requireNotNull(holder.get())
        context.deliver(PermissionDecision.AllowAlways)
        yield()
        assertFalse(decision.isCompleted)

        context.onAbandoned()

        assertEquals(PermissionDecision.AllowAlways, decision.await())
    }

    @Test
    fun `an abandoned prompt denies rather than stranding the caller`() = runBlocking<Unit> {
        val decision = async { requester.prompt(product, ProductPermission.UserIdentityAccess) }
        yield()

        requireNotNull(holder.get()).onAbandoned()

        assertEquals(PermissionDecision.Deny, decision.await())
    }
}
