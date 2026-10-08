package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket

import io.paritytech.polkadotapp.common.data.network.TestnetEnvironment
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.BackupProgress
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageBackupService
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageHoldingsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageTestnetFundUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ShareCoinageLogsUseCase
import io.paritytech.polkadotapp.feature_products_api.domain.FundingConfig
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_wallet_impl.PocketRouter
import io.paritytech.polkadotapp.feature_wallet_impl.domain.interactor.DigitalDollarCardDetailsInteractor
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.ParkedFundingDomainProvider
import io.paritytech.polkadotapp.test_shared.assertPresentationErrorShown
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private const val ONRAMP_URL = "onramp.example"
private const val OFFRAMP_URL = "offramp.example"

@OptIn(ExperimentalCoroutinesApi::class)
class DigitalDollarCardDetailsViewModelTest {
    private val fundingConfig = FundingConfig(onrampUrl = ONRAMP_URL, offrampUrl = OFFRAMP_URL)

    private val chainAssetProvider: ChainAssetProvider = mock(ChainAssetProvider::class.java)
    private val coinageTestnetFundUseCase: CoinageTestnetFundUseCase = mock(CoinageTestnetFundUseCase::class.java)
    private val coinageBackupService: CoinageBackupService = mock(CoinageBackupService::class.java)
    private val shareCoinageLogsUseCase: ShareCoinageLogsUseCase = mock(ShareCoinageLogsUseCase::class.java)
    private val coinageHoldingsUseCase: CoinageHoldingsUseCase = mock(CoinageHoldingsUseCase::class.java)
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase =
        mock(CoinageBalanceConverterUseCase::class.java)
    private val router: PocketRouter = mock(PocketRouter::class.java)
    private val tokenAmountMapper: TokenAmountMapper = mock(TokenAmountMapper::class.java)

    private val fundingDomainProvider = ParkedFundingDomainProvider()

    private val interactor = DigitalDollarCardDetailsInteractor(
        chainAssetProvider = chainAssetProvider,
        environment = TestnetEnvironment.PRODUCTION,
        coinageTestnetFundUseCase = coinageTestnetFundUseCase,
        coinageBackupService = coinageBackupService,
        shareCoinageLogsUseCase = shareCoinageLogsUseCase,
        fundingDomainProvider = fundingDomainProvider,
        coinageHoldingsUseCase = coinageHoldingsUseCase,
        coinageBalanceConverterUseCase = coinageBalanceConverterUseCase
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(coinageHoldingsUseCase.subscribeHoldings()).thenReturn(emptyFlow())
        whenever(coinageBackupService.subscribeProgress()).thenReturn(flowOf(BackupProgress.NotStarted))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a Get tap opens the top-up sheet`() = runBlocking<Unit> {
        val viewModel = createViewModel()

        viewModel.onGetCashClick()
        withFundingConfigLoaded()

        verifySheetOpenedOnce(ONRAMP_URL)
        verifySheetNeverOpened(OFFRAMP_URL)
    }

    @Test
    fun `a second Get tap while the funding config loads opens one sheet`() = runBlocking<Unit> {
        val viewModel = createViewModel()

        viewModel.onGetCashClick()
        viewModel.onGetCashClick()
        withFundingConfigLoaded()

        verifySheetOpenedOnce(ONRAMP_URL)
    }

    @Test
    fun `after Get opened its sheet the next Get tap opens it again`() = runBlocking<Unit> {
        val viewModel = createViewModel()

        viewModel.onGetCashClick()
        withFundingConfigLoaded()
        viewModel.onGetCashClick()
        withFundingConfigLoaded()

        verifySheetOpenedTwice(ONRAMP_URL)
    }

    @Test
    fun `a failed funding config read shows the top-up error and unlocks Get`() = runBlocking<Unit> {
        val viewModel = createViewModel()

        viewModel.onGetCashClick()
        withFundingConfigFailing()
        viewModel.onGetCashClick()
        withFundingConfigLoaded()

        assertPresentationErrorShown<GetCashUnavailablePresentationError>(viewModel)
        verifySheetOpenedOnce(ONRAMP_URL)
    }

    private fun withFundingConfigLoaded() {
        fundingDomainProvider.completeReads(Result.success(fundingConfig))
    }

    private fun withFundingConfigFailing() {
        fundingDomainProvider.completeReads(Result.failure(IllegalStateException("no config")))
    }

    private fun verifySheetOpenedOnce(url: String) {
        verify(router).openSpaSheet(url)
    }

    private fun verifySheetOpenedTwice(url: String) {
        verify(router, times(2)).openSpaSheet(url)
    }

    private fun verifySheetNeverOpened(url: String) {
        verify(router, never()).openSpaSheet(url)
    }

    private fun createViewModel() = DigitalDollarCardDetailsViewModel(
        interactor = interactor,
        router = router,
        tokenAmountMapper = tokenAmountMapper
    )
}
