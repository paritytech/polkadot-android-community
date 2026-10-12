package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAmountBreakdown
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerFungibility
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinAmountBreakdownUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission.TransferSubmissionParams
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.test_shared.any
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class UnloadAndSplitVouchersStrategyTest {
    private val transactionService: CoinageTransactionService = mock(CoinageTransactionService::class.java)

    private val strategy = UnloadAndSplitVouchersStrategy(
        payload = StrategyType.UnloadAndSplit(
            vouchersToUnload = listOf(voucherInRecycler(index = 1)),
            recipientAmount = BigDecimal.ONE,
            exactCoins = emptyList(),
        ),
        chain = mock(Chain::class.java),
        coinRepository = mock(CoinRepository::class.java),
        transactionService = transactionService,
        coinageTransactionFactory = mock(CoinageTransaction.Factory::class.java),
        breakdownUseCase = HeldBreakdownUseCase(),
        balanceConverterUseCase = mock(CoinageBalanceConverterUseCase::class.java),
    )

    @Test
    fun `a schedule cancelled while it waits is a cancellation, not a failed result`() = runBlocking<Unit> {
        var result: Result<ScheduledTransfer>? = null
        val scheduling = launch { result = with(StalenessReportCollector.NoOp) { strategy.schedule(PARAMS) } }
        yield()

        scheduling.cancelAndJoin()

        assertTrue("the scheduling was not cancelled", scheduling.isCancelled)
        assertNull("a cancelled scheduling produced a result", result)
        verify(transactionService, never()).preCommitHandoff(any())
    }

    private fun voucherInRecycler(index: Int) = RecyclerVoucher(
        ringVrfKeyIndex = testKey(index),
        ringVrfPublicKey = byteArrayOf(index.toByte()).toDataByteArray(),
        recyclerValue = ValueExponent(3),
        location = RecyclerVoucher.Location.InRecycler(RingIndex(BigInteger.ZERO), recyclerMembers = 1, enteredAt = null),
        recyclerFungibility = RecyclerFungibility.NONE,
        maxRecyclerFungibility = RecyclerFungibility.NONE,
    )

    private class HeldBreakdownUseCase : CoinAmountBreakdownUseCase {
        override suspend fun createCoinAmountBreakdown(): Result<CoinAmountBreakdown> = awaitCancellation()
    }

    private companion object {
        val PARAMS = TransferSubmissionParams(buildUntil = Instant.DISTANT_FUTURE, retryFailures = false)
    }
}
