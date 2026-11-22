package io.paritytech.polkadotapp.feature_sso_impl.presentation.pairRequest

import androidx.lifecycle.SavedStateHandle
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.notification.AppNotifier
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_sso_api.domain.model.HandshakeOffer
import io.paritytech.polkadotapp.feature_sso_impl.SsoRouter
import io.paritytech.polkadotapp.feature_sso_impl.domain.pairRequest.PairRequestInteractor
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.argThat
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_api.domain.BiometricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

private const val HOST_NAME = "Test Host"

@OptIn(ExperimentalCoroutinesApi::class)
class PairRequestViewModelTest {
    private val payload = PairRequestPayload(
        statementAccountId = ByteArray(32) { 0x11 },
        encryptionPublicKey = ByteArray(32) { 0x22 },
        metadata = mapOf(MetadataKey.HostName to HOST_NAME),
    )

    private val router: SsoRouter = mock(SsoRouter::class.java)
    private val interactor: PairRequestInteractor = mock(PairRequestInteractor::class.java)
    private val appNotifier: AppNotifier = mock(AppNotifier::class.java)
    private val contextManager: ContextManager = mock(ContextManager::class.java)
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
    fun `starts the handshake when authentication succeeds`() = runBlocking<Unit> {
        withAuthenticationSucceeding()
        withHandshakeVerifying()
        val viewModel = createViewModel()

        viewModel.onApproveClicked()

        verifyHandshakeStartedOnce()
    }

    @Test
    fun `sends no handshake and no notification when authentication is cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val viewModel = createViewModel()

        viewModel.onApproveClicked()

        verifyNoHandshakeStarted()
        verifyNoInteractions(appNotifier)
    }

    @Test
    fun `stays on Confirmation when authentication is cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val viewModel = createViewModel()

        viewModel.onApproveClicked()

        assertEquals(LoadingState.Loaded(confirmation()), viewModel.state.value)
    }

    private suspend fun withAuthenticationSucceeding() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.success(Unit))
    }

    private suspend fun withAuthenticationCancelled() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.failure(AuthenticationCancelledException()))
    }

    private fun withHandshakeVerifying() {
        with(any<StalenessReportCollector>()) {
            whenever(interactor.approveHandshake(any())).thenReturn(flowOf(DeviceOnboardingProgress.Verifying))
        }
    }

    private fun verifyHandshakeStartedOnce() {
        val verifier = verify(interactor, times(1))
        with(any<StalenessReportCollector>()) { verifier.approveHandshake(offerFromPayload()) }
    }

    private fun verifyNoHandshakeStarted() {
        val verifier = verify(interactor, never())
        with(any<StalenessReportCollector>()) { verifier.approveHandshake(any()) }
    }

    private fun offerFromPayload(): HandshakeOffer = argThat {
        it.device.statementAccountId.value.contentEquals(payload.statementAccountId) && it.metadata.hostName == HOST_NAME
    }

    private fun confirmation() = PairRequestUiState.Confirmation(
        PairRequestDeviceUiModel(name = HOST_NAME, hostVersion = null, platformType = null)
    )

    private fun createViewModel() = PairRequestViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PairRequestPayload::class.java.name to payload)),
        router = router,
        interactor = interactor,
        appNotifier = appNotifier,
        contextManager = contextManager,
        biometricsService = biometricsService,
    )
}
