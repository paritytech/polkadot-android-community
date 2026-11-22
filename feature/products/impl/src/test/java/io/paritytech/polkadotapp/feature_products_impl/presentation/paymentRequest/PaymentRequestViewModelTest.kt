package io.paritytech.polkadotapp.feature_products_impl.presentation.paymentRequest

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.paymentRequest.PaymentRequestContext
import io.paritytech.polkadotapp.feature_products_impl.domain.paymentRequest.PaymentRequestContext.Decision
import io.paritytech.polkadotapp.feature_products_impl.domain.paymentRequest.PaymentRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.domain.paymentRequest.PaymentRequestStep
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.assertNeverCompletes
import io.paritytech.polkadotapp.test_shared.cancelScopeAndJoin
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_api.domain.BiometricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.math.BigInteger
import kotlin.time.Duration.Companion.seconds

private val TIMEOUT = 1.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentRequestViewModelTest {
    private val router: ProductsRouter = mock(ProductsRouter::class.java)
    private val holder: PaymentRequestContextHolder = mock(PaymentRequestContextHolder::class.java)
    private val chainAssetProvider: ChainAssetProvider = mock(ChainAssetProvider::class.java)
    private val tokenAmountMapper: TokenAmountMapper = mock(TokenAmountMapper::class.java)
    private val biometricsService: BiometricsService = mock(BiometricsService::class.java)

    private var createdViewModel: PaymentRequestViewModel? = null

    @Before
    fun setUp() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        withDisplayableAmount()
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        createdViewModel?.cancelScopeAndJoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `keeps the Confirm step and delivers nothing when authentication is cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val context = contextWithSteps(PaymentRequestStep.Confirm)
        val viewModel = createViewModel(context)
        viewModel.awaitStep(PaymentRequestStep.Confirm)

        viewModel.onApproveClicked()

        assertCurrentStep(PaymentRequestStep.Confirm, viewModel)
        assertNoDecisionDelivered(context)
        verifyScreenStaysOpen()
    }

    @Test
    fun `keeps the Confirm step when authentication is cancelled and a privacy warning follows`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val context = contextWithSteps(PaymentRequestStep.Confirm, PaymentRequestStep.PrivacyWarning)
        val viewModel = createViewModel(context)
        viewModel.awaitStep(PaymentRequestStep.Confirm)
        viewModel.onApproveClicked()
        withAuthenticationSucceeding()

        viewModel.onApproveClicked()

        viewModel.awaitStep(PaymentRequestStep.PrivacyWarning)
        assertNoDecisionDelivered(context)
        verifyScreenStaysOpen()
    }

    @Test
    fun `advances to the privacy warning when authentication succeeds on Confirm`() = runBlocking<Unit> {
        withAuthenticationSucceeding()
        val context = contextWithSteps(PaymentRequestStep.Confirm, PaymentRequestStep.PrivacyWarning)
        val viewModel = createViewModel(context)

        viewModel.onApproveClicked()

        viewModel.awaitStep(PaymentRequestStep.PrivacyWarning)
        assertNoDecisionDelivered(context)
    }

    @Test
    fun `delivers Approved when authentication succeeds on the last Confirm step`() = runBlocking<Unit> {
        withAuthenticationSucceeding()
        val context = contextWithSteps(PaymentRequestStep.Confirm)
        val viewModel = createViewModel(context)

        viewModel.onApproveClicked()

        assertEquals(Decision.Approved, awaitDecision(context))
        verifyScreenClosed()
    }

    @Test
    fun `approves the privacy warning without authentication`() = runBlocking<Unit> {
        val context = contextWithSteps(PaymentRequestStep.PrivacyWarning)
        val viewModel = createViewModel(context)

        viewModel.onApproveClicked()

        assertEquals(Decision.Approved, awaitDecision(context))
        verifyAuthenticationNeverRequested()
    }

    private suspend fun withDisplayableAmount() {
        whenever(chainAssetProvider.asset()).thenReturn(mock(Chain.Asset::class.java))
        whenever(tokenAmountMapper.mapFrom(any())).thenReturn(TokenAmountModel.mock(value = 5))
    }

    private suspend fun withAuthenticationSucceeding() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.success(Unit))
    }

    private suspend fun withAuthenticationCancelled() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.failure(AuthenticationCancelledException()))
    }

    private fun contextWithSteps(vararg steps: PaymentRequestStep) = PaymentRequestContext(
        productId = ProductId.fromStoredValue("product.dot"),
        amount = BigInteger.valueOf(5).intoBalance(),
        steps = steps.toList(),
    )

    private suspend fun PaymentRequestViewModel.awaitStep(step: PaymentRequestStep) {
        withTimeout(TIMEOUT) {
            state.first { it is LoadingState.Loaded && it.data.step == step }
        }
    }

    private fun assertCurrentStep(expected: PaymentRequestStep, viewModel: PaymentRequestViewModel) {
        assertEquals(expected, (viewModel.state.value as? LoadingState.Loaded)?.data?.step)
    }

    private suspend fun awaitDecision(context: PaymentRequestContext): Decision =
        withTimeout(TIMEOUT) { context.awaitDecision() }

    private suspend fun assertNoDecisionDelivered(context: PaymentRequestContext) =
        assertNeverCompletes("expected no decision") { context.awaitDecision() }

    private fun verifyScreenStaysOpen() {
        verify(router, never()).back()
    }

    private fun verifyScreenClosed() {
        verify(router).back()
    }

    private suspend fun verifyAuthenticationNeverRequested() {
        verify(biometricsService, never()).performOneTimeAuthentication()
    }

    private fun createViewModel(context: PaymentRequestContext) = PaymentRequestViewModel(
        router = router,
        context = context,
        holder = holder,
        chainAssetProvider = chainAssetProvider,
        tokenAmountMapper = tokenAmountMapper,
        biometricsService = biometricsService,
    ).also { createdViewModel = it }
}
