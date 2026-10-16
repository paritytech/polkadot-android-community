package io.paritytech.polkadotapp.app.root.presentation.root

import io.paritytech.polkadotapp.app.root.domain.RootInteractor
import io.paritytech.polkadotapp.app.root.presentation.main.BottomNavHeightProvider
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.utils.awaitTrue
import io.paritytech.polkadotapp.feature_chats_api.domain.chatRequest.ChatRequestServiceCoordinator
import io.paritytech.polkadotapp.feature_chats_api.domain.middleware.bot.ChatBotStateController
import io.paritytech.polkadotapp.feature_chats_impl.domain.ChatEngine
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentWorkerStarter
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageServiceStarter
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthMixin
import io.paritytech.polkadotapp.feature_fund_api.domain.AutoConvertDepositService
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreProductsService
import io.paritytech.polkadotapp.feature_products_impl.domain.funding.FundingProductsWarmUp
import io.paritytech.polkadotapp.feature_settings_impl.domain.interactors.SyncPriceCurrencyChange
import io.paritytech.polkadotapp.feature_sso_impl.domain.SsoService
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlotAllocator
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.ObserveAccountOnboardingStatusUseCase
import io.paritytech.polkadotapp.test_shared.TestCoroutineDispatchers
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_jwt_auth_impl.domain.warmUp.JwtAuthWarmUpService
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.verification.VerificationMode

/**
 * Prices, deposits, currency sync, statement store slots, update systems and the web product warm-up all read
 * synced remote config, so they start once the first sync lands, however late, and only once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private val remoteConfigSynced = MutableStateFlow(false)
    private val remoteConfigService = object : RemoteConfigService by mock(RemoteConfigService::class.java) {
        override suspend fun awaitSynced() = remoteConfigSynced.awaitTrue()
    }

    private var coinageServicesStarted = false
    private val coinageServiceStarter = object : CoinageServiceStarter {
        context(scope: ComputationalScope)
        override fun start() {
            coinageServicesStarted = true
        }
    }

    private var depositObservationStarts = 0
    private val depositService = object : AutoConvertDepositService by mock(AutoConvertDepositService::class.java) {
        context(scope: ComputationalScope)
        override suspend fun startObserveAndConvert() {
            depositObservationStarts++
        }
    }

    private val rootInteractor: RootInteractor = mock(RootInteractor::class.java)
    private val syncPriceCurrencyChange: SyncPriceCurrencyChange = mock(SyncPriceCurrencyChange::class.java)
    private val externalPaymentWorkerStarter: ExternalPaymentWorkerStarter =
        mock(ExternalPaymentWorkerStarter::class.java)
    private val statementStoreSlotAllocator: StatementStoreSlotAllocator =
        mock(StatementStoreSlotAllocator::class.java)
    private val ssoService: SsoService = mock(SsoService::class.java)
    private val fundingProductsWarmUp: FundingProductsWarmUp = mock(FundingProductsWarmUp::class.java)
    private val chatEngine: ChatEngine = mock(ChatEngine::class.java)
    private val observeAccountOnboardingStatus: ObserveAccountOnboardingStatusUseCase =
        mock(ObserveAccountOnboardingStatusUseCase::class.java)
    private val chainHealthMixin: ChainHealthMixin = mock(ChainHealthMixin::class.java)
    private val chainHealthMixinFactory: ChainHealthMixin.Factory = mock(ChainHealthMixin.Factory::class.java)

    @Before
    fun setUp() = runBlocking<Unit> {
        Dispatchers.setMain(dispatcher)
        withRootScreenDependencies()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `config-dependent services wait for remote config to sync`() = runBlocking<Unit> {
        createViewModel()

        verifyConfigDependentServicesNotStarted()
    }

    @Test
    fun `config-dependent services start once remote config syncs`() = runBlocking<Unit> {
        createViewModel()

        withRemoteConfigSynced()

        verifyConfigDependentServicesStartedOnce()
    }

    @Test
    fun `durability services start without waiting for remote config`() = runBlocking<Unit> {
        createViewModel()

        assertDurabilityServicesStarted()
    }

    private fun createViewModel() = RootViewModel(
        rootInteractor = rootInteractor,
        syncPriceCurrencyChange = syncPriceCurrencyChange,
        deeplinkHandler = mock(),
        splashPassedObserver = mock(),
        remoteConfigService = remoteConfigService,
        depositService = depositService,
        coinageServiceStarter = coinageServiceStarter,
        externalPaymentWorkerStarter = externalPaymentWorkerStarter,
        statementStoreSlotAllocator = statementStoreSlotAllocator,
        ssoService = ssoService,
        chatRequestServiceCoordinator = mock(ChatRequestServiceCoordinator::class.java),
        exploreProductsService = mock(ExploreProductsService::class.java),
        fundingProductsWarmUp = fundingProductsWarmUp,
        jwtAuthWarmUpService = mock(JwtAuthWarmUpService::class.java),
        chatBotStateController = mock(ChatBotStateController::class.java),
        chatEngine = chatEngine,
        observeAccountOnboardingStatus = observeAccountOnboardingStatus,
        bottomNavHeightProvider = BottomNavHeightProvider(),
        chainHealthMixinFactory = chainHealthMixinFactory,
        coroutineDispatchers = TestCoroutineDispatchers(dispatcher),
    )

    private suspend fun withRootScreenDependencies() {
        whenever(rootInteractor.isDevResetNeeded()).thenReturn(false)
        whenever(rootInteractor.startUpdateSystems()).thenReturn(emptyFlow<Unit>())
        whenever(chatEngine.observeActiveOverlays()).thenReturn(emptyFlow())
        whenever(observeAccountOnboardingStatus()).thenReturn(emptyFlow())
        whenever(ssoService.watchSsoEvents()).thenReturn(emptyFlow())
        whenever(chainHealthMixin.model).thenReturn(MutableStateFlow(ChainHealthIndicatorsModel(persistentListOf())))
        whenever(chainHealthMixinFactory.create(any())).thenReturn(chainHealthMixin)
    }

    private fun withRemoteConfigSynced() {
        remoteConfigSynced.value = true
    }

    private suspend fun verifyConfigDependentServicesNotStarted() {
        verifyConfigDependentServices(never())
        assertEquals(0, depositObservationStarts)
    }

    private suspend fun verifyConfigDependentServicesStartedOnce() {
        verifyConfigDependentServices(times(1))
        assertEquals(1, depositObservationStarts)
    }

    private suspend fun verifyConfigDependentServices(mode: VerificationMode) {
        verify(rootInteractor, mode).syncPrices()
        verify(rootInteractor, mode).startUpdateSystems()
        verify(syncPriceCurrencyChange, mode).startObserving()
        verify(statementStoreSlotAllocator, mode).scheduleSlotRenewals()
        verify(fundingProductsWarmUp, mode).warmUp()
    }

    private fun assertDurabilityServicesStarted() {
        assertTrue("coinage services were not started", coinageServicesStarted)
        verify(externalPaymentWorkerStarter).start()
    }
}
