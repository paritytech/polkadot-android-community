package io.paritytech.polkadotapp.feature_products_impl.presentation.crossProductProof

import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof.CrossProductProofContext
import io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof.CrossProductProofContext.Decision
import io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof.CrossProductProofContextHolder
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.test_shared.assertNeverCompletes
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_api.domain.BiometricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.time.Duration.Companion.seconds

private val DECISION_TIMEOUT = 1.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CrossProductProofViewModelTest {
    private val context = CrossProductProofContext(
        callingProduct = ProductId.fromStoredValue("caller.dot"),
        onBehalfOf = ProductId.fromStoredValue("target.dot"),
        suffix = DerivationIndex32.fromUInt(1u),
        message = "proof".encodeToByteArray().toDataByteArray(),
    )

    private val router: ProductsRouter = mock(ProductsRouter::class.java)
    private val holder: CrossProductProofContextHolder = mock(CrossProductProofContextHolder::class.java)
    private val biometricsService: BiometricsService = mock(BiometricsService::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delivers no decision and stays open when authentication is cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val viewModel = createViewModel()

        viewModel.onApproveClicked()

        assertNoDecisionDelivered()
        verifyScreenStaysOpen()
    }

    @Test
    fun `delivers Approved and closes when authentication succeeds`() = runBlocking<Unit> {
        withAuthenticationSucceeding()
        val viewModel = createViewModel()

        viewModel.onApproveClicked()

        assertEquals(Decision.Approved, awaitDecision())
        verifyScreenClosed()
    }

    private suspend fun withAuthenticationSucceeding() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.success(Unit))
    }

    private suspend fun withAuthenticationCancelled() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.failure(AuthenticationCancelledException()))
    }

    private suspend fun awaitDecision(): Decision = withTimeout(DECISION_TIMEOUT) { context.awaitDecision() }

    private suspend fun assertNoDecisionDelivered() =
        assertNeverCompletes("expected no decision") { context.awaitDecision() }

    private fun verifyScreenStaysOpen() {
        verify(router, never()).back()
    }

    private fun verifyScreenClosed() {
        verify(router).back()
    }

    private fun createViewModel() = CrossProductProofViewModel(
        router = router,
        context = context,
        holder = holder,
        biometricsService = biometricsService,
    )
}
