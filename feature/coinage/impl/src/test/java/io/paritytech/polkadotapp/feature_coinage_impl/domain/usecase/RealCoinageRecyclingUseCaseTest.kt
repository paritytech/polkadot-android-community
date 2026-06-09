package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.EnabledChainConnectionReference
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.VoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.VoucherRingDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class RealCoinageRecyclingUseCaseTest {
    private val coinRepository: CoinRepository = mock()
    private val voucherAllocator: VoucherAllocator = mock()
    private val voucherRingDerivation: VoucherRingDerivation = mock()
    private val voucherRepository: VoucherRepository = mock()
    private val coinageTransactionOrigins: CoinageTransactionOrigins = mock()
    private val chainConnectionRefCounter: ChainConnectionRefCounter = mock()
    private val chainRegistry: ChainRegistry = mock()
    private val extrinsicService: ExtrinsicService = mock()
    private val chainAssetProvider: ChainAssetProvider = mock()

    private val recyclingAge = 14
    private val chainId = "test-chain-id"

    private val useCase: RealCoinageRecyclingUseCase

    init {
        whenever(coinRepository.getCoinRecyclingAge()).thenReturn(recyclingAge)
        whenever(chainAssetProvider.chainId()).thenReturn(chainId)

        runBlocking {
            val mockRef: EnabledChainConnectionReference = mock()
            whenever(chainConnectionRefCounter.requestConnectionEnabled(any<Set<String>>(), any())).thenReturn(mockRef)
            whenever(chainRegistry.getChain(any<String>())).thenReturn(mock<Chain>())
        }

        useCase = RealCoinageRecyclingUseCase(
            coinRepository = coinRepository,
            voucherAllocator = voucherAllocator,
            voucherRingDerivation = voucherRingDerivation,
            coinageTransactionOrigins = coinageTransactionOrigins,
            chainConnectionRefCounter = chainConnectionRefCounter,
            chainRegistry = chainRegistry,
            extrinsicService = extrinsicService,
            chainAssetProvider = chainAssetProvider,
            voucherRepository = voucherRepository
        )
    }

    @Test
    fun `returns success when no coins to recycle`() {
        runBlocking {
            whenever(coinRepository.getActiveCoinsWithKnownAge(recyclingAge)).thenReturn(emptyList())

            val result = useCase()

            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun `uses recycling age from repository as min age filter`() {
        runBlocking {
            whenever(coinRepository.getActiveCoinsWithKnownAge(recyclingAge)).thenReturn(emptyList())

            useCase()

            verify(coinRepository).getCoinRecyclingAge()
            verify(coinRepository).getActiveCoinsWithKnownAge(recyclingAge)
        }
    }

    @Test
    fun `marks coins as spent locally before batch submission`() {
        runBlocking {
            val coins = listOf(createCoin(exponent = 1), createCoin(exponent = 2))
            whenever(coinRepository.getActiveCoinsWithKnownAge(recyclingAge)).thenReturn(coins)
            whenever(extrinsicService.submitExtrinsicsAndAwaitInBlock(any(), any(), any(), any()))
                .thenReturn(Result.success(emptyList()))

            useCase()

            val expectedSpentCoins = coins.map { it.copy(spentState = Coin.SpentState.SPENT_LOCALLY) }
            verify(coinRepository).saveAll(expectedSpentCoins)
        }
    }

    @Test
    fun `rolls back coin status when batch submission fails`() {
        runBlocking {
            val coins = listOf(createCoin(exponent = 1))
            val voucher = createVoucher(ringVrfKeyIndex = 5, exponent = 1)
            whenever(coinRepository.getActiveCoinsWithKnownAge(recyclingAge)).thenReturn(coins)
            whenever(voucherAllocator.allocate(ValueExponent(1))).thenReturn(Result.success(voucher))
            whenever(extrinsicService.submitExtrinsicsAndAwaitInBlock(any(), any(), any(), any()))
                .thenReturn(Result.failure(RuntimeException("Batch failed")))

            useCase()

            val rolledBackCoins = coins.map { it.copy(spentState = Coin.SpentState.NOT_SPENT) }
            verify(coinRepository).saveAll(rolledBackCoins)
            verify(voucherRepository).removeVouchers(listOf(voucher.ringVrfKeyIndex))
        }
    }

    @Test
    fun `returns failure when batch submission fails`() = runBlocking {
        val coins = listOf(createCoin(exponent = 1))
        whenever(coinRepository.getActiveCoinsWithKnownAge(recyclingAge)).thenReturn(coins)
        whenever(extrinsicService.submitExtrinsicsAndAwaitInBlock(any(), any(), any(), any()))
            .thenReturn(Result.failure(RuntimeException("Batch failed")))

        val result = useCase()

        assertTrue(result.isFailure)
    }

    private fun createCoin(exponent: Int): Coin {
        return Coin(
            derivationIndex = 0,
            valueExponent = ValueExponent(exponent),
            age = Coin.Age.Known(recyclingAge),
            spentState = Coin.SpentState.NOT_SPENT,
            accountId = byteArrayOf(exponent.toByte()).intoAccountId()
        )
    }

    private fun createVoucher(ringVrfKeyIndex: Int, exponent: Int): RecyclerVoucher {
        return RecyclerVoucher(
            ringVrfKeyIndex = ringVrfKeyIndex,
            ringVrfPublicKey = byteArrayOf(ringVrfKeyIndex.toByte()).toDataByteArray(),
            recyclerValue = ValueExponent(exponent),
            location = RecyclerVoucher.Location.Unknown,
            allocatedAt = 0L,
            delayUnloadUntil = 0L,
            ringHasEnoughRingMembersToWithdraw = false,
            usageState = RecyclerVoucher.UsageState.NOT_USED
        )
    }
}
