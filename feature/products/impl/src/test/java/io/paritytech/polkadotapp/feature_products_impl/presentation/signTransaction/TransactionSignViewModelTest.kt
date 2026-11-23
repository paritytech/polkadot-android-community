package io.paritytech.polkadotapp.feature_products_impl.presentation.signTransaction

import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_products_api.model.signing.SignedTransaction
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContext
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContextHolder
import io.paritytech.polkadotapp.feature_products_impl.domain.signTransaction.TransactionSignInteractor
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.assertNothingShown
import io.paritytech.polkadotapp.test_shared.cancelScopeAndJoin
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_api.domain.BiometricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSignViewModelTest {
    private val signed = SignedTransaction.Raw(signature = byteArrayOf(0x01).toDataByteArray())

    private val router: ProductsRouter = mock(ProductsRouter::class.java)
    private val interactor: TransactionSignInteractor = mock(TransactionSignInteractor::class.java)
    private val signingContextHolder: SigningContextHolder = mock(SigningContextHolder::class.java)
    private val signingContext: SigningContext = mock(SigningContext::class.java)
    private val biometricsService: BiometricsService = mock(BiometricsService::class.java)

    private var createdViewModel: TransactionSignViewModel? = null

    @Before
    fun setUp() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        withNoSigningPreview()
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        createdViewModel?.cancelScopeAndJoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `signs and delivers the result when authentication succeeds`() = runBlocking<Unit> {
        withAuthenticationSucceeding()
        withSuccessfulSigning()
        val viewModel = createViewModel()

        viewModel.onApproveClicked()

        verifySignedDeliveredAndClosed()
    }

    @Test
    fun `neither signs, delivers nor rejects when authentication is cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val viewModel = createViewModel()

        viewModel.onApproveClicked()

        verifyNothingSignedOrDelivered()
        verifyScreenStaysOpen()
    }

    @Test
    fun `shows no error when authentication is cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val viewModel = createViewModel()

        viewModel.onApproveClicked()

        assertNothingShown(viewModel)
    }

    @Test
    fun `prompts again on a second Approve when the first authentication was cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val viewModel = createViewModel()

        viewModel.onApproveClicked()
        viewModel.onApproveClicked()

        verifyAuthenticationRequested(count = 2)
    }

    private suspend fun withNoSigningPreview() {
        whenever(interactor.parseSigningContent()).thenReturn(Result.failure(IllegalStateException("no preview")))
        whenever(interactor.humanReadableRepresentation()).thenReturn(Result.failure(IllegalStateException("no preview")))
    }

    private suspend fun withAuthenticationSucceeding() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.success(Unit))
    }

    private suspend fun withAuthenticationCancelled() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.failure(AuthenticationCancelledException()))
    }

    private suspend fun withSuccessfulSigning() {
        whenever(interactor.sign()).thenReturn(Result.success(signed))
        whenever(signingContext.deliverSignedResult(signed)).thenReturn(Result.success(Unit))
    }

    private suspend fun verifySignedDeliveredAndClosed() {
        val order = inOrder(interactor, signingContext, router)
        order.verify(interactor, times(1)).sign()
        order.verify(signingContext, times(1)).deliverSignedResult(signed)
        order.verify(router).back()
    }

    private suspend fun verifyNothingSignedOrDelivered() {
        verify(interactor, never()).sign()
        verify(signingContext, never()).deliverSignedResult(any())
        verify(signingContext, never()).deliverRejection()
    }

    private fun verifyScreenStaysOpen() {
        verify(router, never()).back()
    }

    private suspend fun verifyAuthenticationRequested(count: Int) {
        verify(biometricsService, times(count)).performOneTimeAuthentication()
    }

    private fun createViewModel() = TransactionSignViewModel(
        router = router,
        interactor = interactor,
        signingContextHolder = signingContextHolder,
        signingContext = signingContext,
        biometricsService = biometricsService,
    ).also { createdViewModel = it }
}
