package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount

import androidx.lifecycle.SavedStateHandle
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.domain.validation.Validation
import io.paritytech.polkadotapp.common.domain.validation.ValidationProcess
import io.paritytech.polkadotapp.common.domain.validation.ValidationResult
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddress
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddressParcel
import io.paritytech.polkadotapp.feature_tokens_api.presentation.amountinput.AmountInput
import io.paritytech.polkadotapp.feature_tokens_api.presentation.amountinput.AmountInputMixin
import io.paritytech.polkadotapp.feature_tokens_api.presentation.amountinput.AmountInputValue
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.feature_wallet_api.presentation.enterAmount.SendEnterAmountPayload
import io.paritytech.polkadotapp.feature_wallet_api.presentation.enterAmount.TransferMethodPayload
import io.paritytech.polkadotapp.feature_wallet_impl.PocketRouter
import io.paritytech.polkadotapp.feature_wallet_impl.domain.model.AvailableToSendAmount
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.SendEnterAmountUiState.SendProgress
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain.SendEnterAmountInteractor
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain.SendState
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain.SendValidationPayload
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.assertNothingShown
import io.paritytech.polkadotapp.test_shared.cancelScopeAndJoin
import io.paritytech.polkadotapp.test_shared.thenSuspend
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_api.domain.BiometricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.math.BigInteger
import kotlin.time.Duration.Companion.seconds

private val STATE_TIMEOUT = 1.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SendEnterAmountViewModelTest {
    private val recipient = ExtractedAddressParcel(
        display = "anchorBay.17",
        type = ExtractedAddress.DisplayType.USERNAME,
        accountId = ByteArray(32) { 0x01 },
    )
    private val payload = SendEnterAmountPayload(
        showTransactionResult = true,
        transferMethod = TransferMethodPayload.CoinsViaChat(recipient),
        amountPreset = null,
    )

    private val walletRouter: PocketRouter = mock(PocketRouter::class.java)
    private val interactor: SendEnterAmountInteractor = mock(SendEnterAmountInteractor::class.java)
    private val tokenAmountMapper: TokenAmountMapper = mock(TokenAmountMapper::class.java)
    private val biometricsService: BiometricsService = mock(BiometricsService::class.java)
    private val amountInputMixinFactory: AmountInputMixin.Factory = mock(AmountInputMixin.Factory::class.java)
    private val amountInputMixin: AmountInputMixin = mock(AmountInputMixin::class.java)
    private val chainAsset: Chain.Asset = mock(Chain.Asset::class.java)

    private var createdViewModel: SendEnterAmountViewModel? = null

    @Before
    fun setUp() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        withEnteredAmount()
        withTokenBalance()
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        createdViewModel?.cancelScopeAndJoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `submits the transfer when validation passes and authentication succeeds`() = runBlocking<Unit> {
        withValidationPassed()
        withAuthenticationSucceeding()
        withCompletingTransfer()
        val viewModel = createViewModel()

        viewModel.onConfirmClick()

        verifyTransferSubmittedOnce()
    }

    @Test
    fun `does not request authentication when validation is aborted`() = runBlocking<Unit> {
        withValidationAborted()
        val viewModel = createViewModel()

        viewModel.onConfirmClick()

        verifyAuthenticationNeverRequested()
        verifyNoTransferSubmitted()
    }

    @Test
    fun `does not submit when authentication is cancelled`() = runBlocking<Unit> {
        withValidationPassed()
        withAuthenticationCancelled()
        val viewModel = createViewModel()

        viewModel.onConfirmClick()

        verifyNoTransferSubmitted()
        assertNothingShown(viewModel)
    }

    @Test
    fun `returns progress to Idle when authentication is cancelled`() = runBlocking<Unit> {
        withValidationPassed()
        val viewModel = createViewModel()
        viewModel.awaitLoaded()
        withAuthenticationCancelledAfter { viewModel.awaitState { it.sendProgress == SendProgress.Submitting } }

        viewModel.onConfirmClick()

        viewModel.awaitState { it.sendProgress == SendProgress.Idle }
    }

    private fun withEnteredAmount() {
        val amount = AmountInputValue(AmountInput("1", AmountInput.Origin.USER), TokenAmountModel.mock(value = 1))
        whenever(amountInputMixin.value).thenReturn(flowOf(amount))
        whenever(amountInputMixin.availableBalance).thenReturn(flowOf(TokenAmountModel.mock(value = 10)))
        whenever(amountInputMixinFactory.create(any(), any(), any(), any())).thenReturn(amountInputMixin)
    }

    private fun withTokenBalance() {
        val balance = AvailableToSendAmount(
            spendable = BigInteger.TEN.intoBalance(),
            gainingPrivacy = BigInteger.ZERO.intoBalance(),
            canSpendGainingPrivacy = false,
            chainAsset = chainAsset,
        )
        whenever(interactor.tokenBalance()).thenReturn(flowOf(balance))
        whenever(interactor.observeDebugWidgetsEnabled()).thenReturn(flowOf(false))
        whenever(tokenAmountMapper.mapFrom(any())).thenReturn(TokenAmountModel.mock(value = 10))
    }

    private fun withCompletingTransfer() {
        with(any<StalenessReportCollector>()) {
            whenever(interactor.send(any(), any())).thenReturn(flowOf(SendState.Complete(unfinalizedCoins = null)))
        }
    }

    private fun withValidationPassed() {
        whenever(interactor.sendValidation).thenReturn(FixedValidation { ValidationResult.Success(it) })
    }

    private fun withValidationAborted() {
        whenever(interactor.sendValidation).thenReturn(FixedValidation { ValidationResult.Aborted })
    }

    private suspend fun withAuthenticationSucceeding() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.success(Unit))
    }

    private suspend fun withAuthenticationCancelled() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.failure(AuthenticationCancelledException()))
    }

    private suspend fun withAuthenticationCancelledAfter(whilePrompting: suspend () -> Unit) {
        whenever(biometricsService.performOneTimeAuthentication()).thenSuspend {
            whilePrompting()
            Result.failure<Unit>(AuthenticationCancelledException())
        }
    }

    private fun verifyTransferSubmittedOnce() {
        val verifier = verify(interactor, times(1))
        with(any<StalenessReportCollector>()) { verifier.send(any(), any()) }
    }

    private fun verifyNoTransferSubmitted() {
        val verifier = verify(interactor, never())
        with(any<StalenessReportCollector>()) { verifier.send(any(), any()) }
    }

    private suspend fun verifyAuthenticationNeverRequested() {
        verify(biometricsService, never()).performOneTimeAuthentication()
    }

    private suspend fun SendEnterAmountViewModel.awaitLoaded() = awaitState { true }

    private suspend fun SendEnterAmountViewModel.awaitState(predicate: (SendEnterAmountUiState) -> Boolean) {
        withTimeout(STATE_TIMEOUT) {
            state.first { it is LoadingState.Loaded && predicate(it.data) }
        }
    }

    private fun createViewModel() = SendEnterAmountViewModel(
        savedStateHandle = SavedStateHandle(mapOf(SendEnterAmountPayload::class.java.name to payload)),
        amountInputMixinFactory = amountInputMixinFactory,
        walletRouter = walletRouter,
        interactor = interactor,
        tokenAmountMapper = tokenAmountMapper,
        biometricsService = biometricsService,
    ).also { createdViewModel = it }

    private class FixedValidation(
        private val result: (SendValidationPayload) -> ValidationResult<SendValidationPayload>,
    ) : Validation<SendValidationPayload> {
        context(validationProcess: ValidationProcess)
        override suspend fun validate(payload: SendValidationPayload) = result(payload)
    }
}
