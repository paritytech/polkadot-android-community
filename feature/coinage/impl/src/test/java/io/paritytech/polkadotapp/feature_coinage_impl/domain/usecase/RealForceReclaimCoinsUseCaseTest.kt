package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.novasama.substrate_sdk_android.encrypt.keypair.Keypair
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.utils.filterNotNull
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinageBalanceConversionContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ReclaimOutcome
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.CoinKeypairDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigInteger

class RealForceReclaimCoinsUseCaseTest {
    private val coinRepository: CoinRepository = mock()
    private val coinKeypairDerivation: CoinKeypairDerivation = mock()
    private val coinageTransferSubmissionUseCase: CoinageTransferSubmissionUseCase = mock()
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase = mock()
    private val conversionContext: CoinageBalanceConversionContext = mock()
    private val chainAssetProvider: ChainAssetProvider = mock()

    private var presentOnChainCoins: Map<AccountId, OnChainCoinInfo> = emptyMap()

    private val useCase = RealForceReclaimCoinsUseCase(
        coinRepository = coinRepository,
        coinKeypairDerivation = coinKeypairDerivation,
        coinageTransferSubmissionUseCase = coinageTransferSubmissionUseCase,
        coinageBalanceConverterUseCase = coinageBalanceConverterUseCase,
        chainAssetProvider = chainAssetProvider,
    )

    @Test
    fun `returns nothing to reclaim and skips submission when no spent-locally coins`() = runBlocking {
        withSpentLocallyCoins()

        val result = useCase()

        assertEquals(ReclaimOutcome.NothingToReclaim, result.getOrNull())
        verifyNoSubmission()
    }

    @Test
    fun `returns nothing to reclaim and skips submission when no coin is present on-chain`() = runBlocking {
        val coin = coin(derivationIndex = 1, exponent = 3)
        withSpentLocallyCoins(coin)
        withOnChainCoins(coin.missingOnChain())

        val result = useCase()

        assertEquals(ReclaimOutcome.NothingToReclaim, result.getOrNull())
        verifyNoSubmission()
    }

    @Test
    fun `reclaims only on-chain present coins and returns their summed balance`() = runBlocking {
        val present = coin(derivationIndex = 1, exponent = 3)
        val absent = coin(derivationIndex = 2, exponent = 5)
        withSpentLocallyCoins(present, absent)
        withOnChainCoins(present present onChainInfo(exponent = 3), absent.missingOnChain())
        withSuccessfulSubmission()
        withBalanceConversion(exponent = 3, balance = balance(8))

        val result = useCase()

        assertEquals(ReclaimOutcome.Reclaimed(balance(8)), result.getOrNull())
        verifySubmittedExactlyPresentCoins()
    }

    @Test
    fun `propagates failure when submission fails`() = runBlocking {
        val coin = coin(derivationIndex = 1, exponent = 3)
        withSpentLocallyCoins(coin)
        withOnChainCoins(coin present onChainInfo(exponent = 3))
        withFailingSubmission()

        val result = useCase()

        assertTrue(result.isFailure)
    }

    // region — arrange helpers

    private suspend fun withSpentLocallyCoins(vararg coins: Coin) {
        whenever(coinRepository.getCoinsWithSpentState(Coin.SpentState.SPENT_LOCALLY)).thenReturn(coins.toList())
        whenever(chainAssetProvider.chainId()).thenReturn(CHAIN_ID)
    }

    private suspend fun withOnChainCoins(vararg entries: Pair<Coin, OnChainCoinInfo?>) {
        val onChainInfoByAccount = entries.associate { (coin, info) -> coin.accountId to info }
        presentOnChainCoins = onChainInfoByAccount.filterNotNull()
        whenever(coinRepository.fetchCoinsInfoFor(any(), any())).thenReturn(Result.success(onChainInfoByAccount))
    }

    private suspend fun withSuccessfulSubmission() {
        whenever(coinKeypairDerivation.deriveKeypairs(any())).thenReturn(listOf(mock<Keypair>()))
        whenever(coinageTransferSubmissionUseCase.invoke(any(), any())).thenReturn(Result.success(Unit))
    }

    private suspend fun withFailingSubmission() {
        whenever(coinKeypairDerivation.deriveKeypairs(any())).thenReturn(listOf(mock<Keypair>()))
        whenever(coinageTransferSubmissionUseCase.invoke(any(), any()))
            .thenReturn(Result.failure(RuntimeException("submission failed")))
    }

    private suspend fun withBalanceConversion(exponent: Int, balance: Balance) {
        whenever(coinageBalanceConverterUseCase.create()).thenReturn(Result.success(conversionContext))
        whenever(conversionContext.formatExponentToBalance(ValueExponent(exponent))).thenReturn(balance)
    }

    // endregion

    // region — verify helpers

    private suspend fun verifyNoSubmission() {
        verify(coinageTransferSubmissionUseCase, never()).invoke(any(), any())
    }

    private suspend fun verifySubmittedExactlyPresentCoins() {
        verify(coinageTransferSubmissionUseCase).invoke(any(), eq(presentOnChainCoins))
    }

    // endregion

    // region — fixtures

    private infix fun Coin.present(info: OnChainCoinInfo): Pair<Coin, OnChainCoinInfo?> = this to info

    private fun Coin.missingOnChain(): Pair<Coin, OnChainCoinInfo?> = this to null

    private fun coin(derivationIndex: Int, exponent: Int): Coin = Coin(
        derivationIndex = derivationIndex,
        valueExponent = ValueExponent(exponent),
        age = Coin.Age.Known(0),
        spentState = Coin.SpentState.SPENT_LOCALLY,
        accountId = byteArrayOf(derivationIndex.toByte()).intoAccountId()
    )

    private fun onChainInfo(exponent: Int): OnChainCoinInfo = OnChainCoinInfo(value = exponent, age = 0)

    private fun balance(planks: Long): Balance = Balance(BigInteger.valueOf(planks))

    // endregion

    private companion object {
        const val CHAIN_ID = "test-chain-id"
    }
}
