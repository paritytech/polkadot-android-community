package io.paritytech.polkadotapp.feature_products_impl.presentation.permissionPrompt

import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.PermissionContextHolder
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.ProductPermissionContext
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import io.paritytech.polkadotapp.feature_products_impl.presentation.createAndClear
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class PermissionPromptViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val router: ProductsRouter = mock()
    private val holder = PermissionContextHolder()

    private val context = permissionContext("lottery.dot")

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the permission prompt closes when it is withdrawn`() = runTest(testDispatcher) {
        PermissionPromptViewModel(context, holder, router)
        val caller = launch { context.awaitDecision {} }
        runCurrent()
        verifyPromptClosed(times = 0)

        caller.cancel()
        runCurrent()

        verifyPromptClosed(times = 1)
    }

    @Test
    fun `the prompt clears its own context when it goes away`() = runTest(testDispatcher) {
        holder.set(context)

        createAndClear { PermissionPromptViewModel(context, holder, router) }

        assertNull(holder.get())
    }

    @Test
    fun `a newer prompt's context survives when an older prompt goes away`() = runTest(testDispatcher) {
        val newer = permissionContext("other.dot")
        holder.set(newer)

        createAndClear { PermissionPromptViewModel(context, holder, router) }

        assertSame(newer, holder.get())
    }

    private fun permissionContext(product: String) = ProductPermissionContext(
        productId = ProductId.fromStoredValue(product),
        permissions = listOf(ProductPermission.AccountAccess("voting.dot")),
    )

    private fun verifyPromptClosed(times: Int) = verify(router, times(times)).back()
}
