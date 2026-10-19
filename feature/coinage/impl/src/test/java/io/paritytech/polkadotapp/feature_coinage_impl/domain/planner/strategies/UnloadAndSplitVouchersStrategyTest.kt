package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerFungibility
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageHandoffCommit
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinAmountBreakdownUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.common.centsToDollar
import io.paritytech.polkadotapp.feature_coinage_impl.common.coinageTestPrecision
import io.paritytech.polkadotapp.feature_coinage_impl.common.testConversionContext
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.common.RealCoinAmountBreakdownContext
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransactionAssets
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission.TransferSubmissionParams
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class UnloadAndSplitVouchersStrategyTest {
    private val chain: Chain = mock()
    private val coinRepository: CoinRepository = mock()
    private val transactionService: CoinageTransactionService = mock()
    private val coinageTransactionFactory: CoinageTransaction.Factory = mock()
    private val transaction: CoinageTransaction = mock()
    private val breakdownUseCase: CoinAmountBreakdownUseCase = mock()
    private val balanceConverterUseCase: CoinageBalanceConverterUseCase = mock()
    private val handoffCommit: CoinageHandoffCommit = mock()
    private val handedOff = listOf(OwnAsset.Coin(testKey(2)))

    private val strategy = UnloadAndSplitVouchersStrategy(
        payload = StrategyType.UnloadAndSplit(
            vouchersToUnload = listOf(voucherInRecycler()),
            recipientAmount = 32.0.centsToDollar(),
            exactCoins = emptyList(),
        ),
        chain = chain,
        coinRepository = coinRepository,
        transactionService = transactionService,
        coinageTransactionFactory = coinageTransactionFactory,
        breakdownUseCase = breakdownUseCase,
        balanceConverterUseCase = balanceConverterUseCase,
    )

    @Test
    fun `prepares the handoff of its outputs when scheduled in PREPARE mode`() = runBlocking<Unit> {
        withVoucherBatchMinted()
        withHandoffAccepted()

        schedule(CoinReservationMode.PREPARE)

        verifyHandoffPrepared()
    }

    @Test
    fun `pre-commits the handoff of its outputs when scheduled in PRECOMMIT mode`() = runBlocking<Unit> {
        withVoucherBatchMinted()
        withHandoffAccepted()

        schedule(CoinReservationMode.PRECOMMIT)

        verifyHandoffPreCommitted()
    }

    private suspend fun schedule(mode: CoinReservationMode) =
        with(StalenessReportCollector.NoOp) { strategy.schedule(PARAMS, mode) }

    private suspend fun withVoucherBatchMinted() {
        whenever(chain.id).thenReturn(CHAIN_ID)
        whenever(coinRepository.fetchMaxConsolidation(any())).thenReturn(Result.success(MAX_CONSOLIDATION))
        whenever(breakdownUseCase.createCoinAmountBreakdown()).thenReturn(
            Result.success(RealCoinAmountBreakdownContext(coinageTestPrecision, testConversionContext, ALLOWED_EXPONENTS))
        )
        whenever(balanceConverterUseCase.create()).thenReturn(Result.success(testConversionContext))
        whenever(coinageTransactionFactory.newTransaction()).thenReturn(transaction)
        whenever(transaction.mintCoins(any(), any())).thenReturn(Result.success(emptyList()))
        whenever(transaction.build()).thenReturn(CoinageTransactionAssets(emptyList(), emptyList(), handedOff))
    }

    private suspend fun withHandoffAccepted() {
        whenever(transactionService.prepareHandoff(any())).thenReturn(Result.success(handoffCommit))
        whenever(transactionService.preCommitHandoff(any())).thenReturn(Result.success(handoffCommit))
    }

    private suspend fun verifyHandoffPrepared() {
        verify(transactionService).prepareHandoff(eq(handedOff))
        verify(transactionService, never()).preCommitHandoff(any())
    }

    private suspend fun verifyHandoffPreCommitted() {
        verify(transactionService).preCommitHandoff(eq(handedOff))
        verify(transactionService, never()).prepareHandoff(any())
    }

    private fun voucherInRecycler() = RecyclerVoucher(
        ringVrfKeyIndex = testKey(1),
        ringVrfPublicKey = mock(),
        recyclerValue = ValueExponent(5),
        location = RecyclerVoucher.Location.InRecycler(RingIndex(1.toBigInteger()), recyclerMembers = FULL_RING, enteredAt = null),
        recyclerFungibility = RecyclerFungibility.NONE,
        maxRecyclerFungibility = RecyclerFungibility.NONE,
    )

    private companion object {
        const val CHAIN_ID = "coinage"
        const val MAX_CONSOLIDATION = 64
        const val FULL_RING = 767
        val ALLOWED_EXPONENTS = (-2..7).map { ValueExponent(it) }.toSet()
        val PARAMS = TransferSubmissionParams(buildUntil = Instant.fromEpochSeconds(1_000), retryFailures = true)
    }
}
