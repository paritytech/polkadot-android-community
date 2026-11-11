package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinProvenance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageAssetState
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageInput
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageScheduledTransactionRequest
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageTransactionId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageTransactionState
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageAssetsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedCoin
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransactionAssets
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission.CoinageSubmissionParams
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus.FAILURE
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus.PENDING
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RealReclaimHandedOffCoinsUseCaseTest {
    private val coinageAssetsUseCase: CoinageAssetsUseCase = mockk()
    private val coinRepository: CoinRepository = mockk()
    private val transactionService: CoinageTransactionService = mockk()
    private val transactionFactory: CoinageTransaction.Factory = mockk()

    private val useCase = RealReclaimHandedOffCoinsUseCase(
        coinageAssetsUseCase = coinageAssetsUseCase,
        coinRepository = coinRepository,
        transactionService = transactionService,
        coinageTransactionFactory = transactionFactory,
        timeProvider = mockk<TimeProvider> { every { now() } returns NOW },
    )

    @Test
    fun `only a handed-off coin still on chain and not already being reclaimed is reclaimed`() = runTest {
        val waiting = coin(1, onChain = true)
        givenCoins(
            TrackedCoin(waiting, handedOff(consumer = null)),
            TrackedCoin(coin(2, onChain = false), handedOff(consumer = null)),
            TrackedCoin(coin(3, onChain = true), handedOff(consumer = PENDING)),
        )
        val transaction = givenTransaction(input = waiting, output = coin(10, onChain = false))
        val scheduled = slot<List<CoinageScheduledTransactionRequest>>()
        coEvery { transactionService.scheduleTransactions(capture(scheduled), any()) } returns
            Result.success(listOf(CoinageTransactionId(1)))

        val result = useCase.reclaim(listOf(waiting.accountId), GROUP)

        assertTrue(result.isSuccess)
        verify(exactly = 1) { transaction.reclaimCoin(waiting) }
        coVerify { transactionService.scheduleTransactions(any(), GROUP) }

        val request = scheduled.captured.single()
        assertEquals(CoinageSubmissionParams.RECLAIM_POLICY_ID, request.policy.id)
        assertEquals(NOW + 5.minutes, CoinageSubmissionParams.decodeTransfer(request.policy.params).getOrThrow().buildUntil)
        assertEquals(listOf(CoinageInput.Coin.Reclaimed(waiting.derivationIndex)), request.inputs)
    }

    @Test
    fun `nothing is scheduled when no coin is left to reclaim`() = runTest {
        givenCoins(TrackedCoin(coin(1, onChain = false), handedOff(consumer = null)))

        val result = useCase.reclaim(listOf(coin(1, onChain = false).accountId), GROUP)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { transactionService.scheduleTransactions(any(), any()) }
    }

    @Test
    fun `a coin reads as failed only while no newer attempt is live`() = runTest {
        every { transactionService.subscribeOperationGroupStatuses(GROUP) } returns flowOf(
            listOf(
                attempt(coinItem = 1, status = FAILURE),
                attempt(coinItem = 2, status = FAILURE),
                attempt(coinItem = 2, status = PENDING),
            )
        )
        coEvery { coinRepository.getCoinsBy(listOf(testKey(1))) } returns listOf(coin(1, onChain = true))

        assertEquals(setOf(coin(1, onChain = true).accountId), useCase.subscribeFailedReclaims(GROUP).first())
    }

    private fun givenCoins(vararg coins: TrackedCoin) {
        every { coinageAssetsUseCase.subscribeCoinsBy(any()) } returns flowOf(coins.toList())
    }

    private fun givenTransaction(input: Coin, output: Coin): CoinageTransaction {
        val transaction: CoinageTransaction = mockk(relaxed = true)
        every { transactionFactory.newTransaction() } returns transaction
        coEvery { transaction.mintCoins(listOf(input.valueExponent), any()) } returns Result.success(listOf(output))
        every { transaction.build() } returns CoinageTransactionAssets(
            inputs = listOf(CoinageInput.Coin.Reclaimed(input.derivationIndex)),
            outputs = listOf(OwnAsset.Coin(output.derivationIndex)),
            handedOff = emptyList(),
        )

        return transaction
    }

    private fun attempt(coinItem: Int, status: DurableTxStatus) = CoinageTransactionState(
        id = CoinageTransactionId(coinItem.toLong()),
        status = status,
        inputs = listOf(CoinageInput.Coin.Own(testKey(coinItem))),
        outputs = emptyList(),
    )

    private fun handedOff(consumer: DurableTxStatus?) =
        CoinageAssetState(handedOff = true, minterStatus = DurableTxStatus.FINALIZED_SUCCESS, consumerStatus = consumer)

    private fun coin(item: Int, onChain: Boolean) = Coin(
        derivationIndex = testKey(item),
        valueExponent = ValueExponent(3),
        age = Coin.Age.Known(1),
        isOnChain = onChain,
        accountId = accountIdOf(item),
        provenance = CoinProvenance.UNKNOWN,
    )

    private fun accountIdOf(item: Int): AccountId = byteArrayOf(item.toByte()).toDataByteArray()

    private companion object {
        val GROUP = CoinageOperationGroupId("chat-reclaim:message-1")
        val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    }
}
