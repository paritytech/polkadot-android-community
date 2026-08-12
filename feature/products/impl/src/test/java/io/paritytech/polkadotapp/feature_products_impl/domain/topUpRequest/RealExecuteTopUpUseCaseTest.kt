package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.OnboardingUseCase
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionSignerSource
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class RealExecuteTopUpUseCaseTest {
    private val coinageTransferUseCase: CoinageTransferUseCase = mock()
    private val onboardingUseCase: OnboardingUseCase = mock()
    private val chainAssetProvider: ChainAssetProvider = mock()

    private val useCase = RealExecuteTopUpUseCase(
        coinageTransferUseCase = coinageTransferUseCase,
        onboardingUseCase = onboardingUseCase,
        chainAssetProvider = chainAssetProvider,
    )

    @Test
    fun `Exact when onboard source claims successfully`() = runBlocking<Unit> {
        withResolvedAsset()
        withOnboardingSucceeds()

        val result = useCase.claim(onboardSource(), 100.intoBalance())

        assertExact(result)
    }

    @Test
    fun `Exact when detected coins match the stated amount`() = runBlocking<Unit> {
        withDetected(100.intoBalance())
        withTransferred(100.intoBalance())

        val result = useCase.claim(coinsSource(), 100.intoBalance())

        assertExact(result)
    }

    @Test
    fun `Partial with detected amount when detected coins differ from the stated amount`() = runBlocking<Unit> {
        withDetected(80.intoBalance())
        withTransferred(80.intoBalance())

        val result = useCase.claim(coinsSource(), 100.intoBalance())

        assertPartial(result, expectedCredited = 80.intoBalance())
    }

    @Test
    fun `failure when coin detection fails`() = runBlocking<Unit> {
        withDetectionError()

        val result = useCase.claim(coinsSource(), 100.intoBalance())

        assertFailure(result)
    }

    @Test
    fun `failure when coin transfer fails`() = runBlocking<Unit> {
        withDetected(100.intoBalance())
        withTransferError()

        val result = useCase.claim(coinsSource(), 100.intoBalance())

        assertFailure(result)
    }

    private fun onboardSource() = TopUpSource.Onboard(TransactionSignerSource.FromAccount(mock<MetaAccount>()))

    private fun coinsSource() = TopUpSource.Coins(emptyList())

    private suspend fun withResolvedAsset() {
        val asset = mock<Chain.Asset>()
        whenever(asset.precision).thenReturn(10)
        whenever(chainAssetProvider.asset()).thenReturn(asset)
    }

    private suspend fun withOnboardingSucceeds() {
        whenever(onboardingUseCase.onboard(any(), any())).thenReturn(Result.success(Unit))
    }

    private suspend fun withDetected(amount: Balance) {
        whenever(coinageTransferUseCase.invoke(eq(false), any(), any()))
            .thenReturn(flowOf(CoinageTransferDetection.Detected(amount)))
    }

    private suspend fun withTransferred(amount: Balance) {
        whenever(coinageTransferUseCase.invoke(eq(true), any(), any()))
            .thenReturn(flowOf(CoinageTransferDetection.Transferred(amount)))
    }

    private suspend fun withDetectionError() {
        whenever(coinageTransferUseCase.invoke(eq(false), any(), any()))
            .thenReturn(flowOf(CoinageTransferDetection.Error.Detection))
    }

    private suspend fun withTransferError() {
        whenever(coinageTransferUseCase.invoke(eq(true), any(), any()))
            .thenReturn(flowOf(CoinageTransferDetection.Error.Transfer))
    }

    private fun assertExact(result: Result<TopUpClaimResult>) {
        assertTrue("expected success but was ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue("expected Exact but was ${result.getOrNull()}", result.getOrNull() is TopUpClaimResult.Exact)
    }

    private fun assertPartial(result: Result<TopUpClaimResult>, expectedCredited: Balance) {
        assertTrue("expected success but was ${result.exceptionOrNull()}", result.isSuccess)
        val outcome = result.getOrNull()
        assertTrue("expected Partial but was $outcome", outcome is TopUpClaimResult.Partial)
        assertEquals(expectedCredited, (outcome as TopUpClaimResult.Partial).credited)
    }

    private fun assertFailure(result: Result<TopUpClaimResult>) {
        assertTrue("expected failure but was ${result.getOrNull()}", result.isFailure)
    }
}
