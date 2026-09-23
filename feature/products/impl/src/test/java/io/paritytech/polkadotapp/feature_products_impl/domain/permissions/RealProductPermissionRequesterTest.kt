package io.paritytech.polkadotapp.feature_products_impl.domain.permissions

import io.mockk.coEvery
import io.mockk.mockk
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RealProductPermissionRequesterTest {
    private val holder = PermissionContextHolder()
    private val router: ProductsRouter = mockk(relaxed = true)
    private val requester = RealProductPermissionRequester(holder, router)

    private val product = ProductId.fromStoredValue("lottery.dot")
    private val permission = ProductPermission.AccountAccess("voting.dot")

    /** The sheet may be created after the caller gave up, so the withdrawn context stays for it to find and close. */
    @Test
    fun `the prompt is withdrawn and left for its sheet when its caller stops waiting`() = runTest {
        val caller = launch { requester.prompt(product, permission) }
        runCurrent()
        val prompt = requireNotNull(holder.get())
        assertFalse(prompt.isWithdrawn)

        caller.cancel()
        runCurrent()

        assertTrue(prompt.isWithdrawn)
        assertSame(prompt, holder.get())
    }

    @Test
    fun `the prompt is withdrawn when its caller stops waiting while it opens`() = runTest {
        coEvery { router.openPermissionPrompt() } coAnswers { awaitCancellation() }
        val caller = launch { requester.prompt(product, permission) }
        runCurrent()

        caller.cancel()
        runCurrent()

        assertTrue(requireNotNull(holder.get()).isWithdrawn)
    }
}
