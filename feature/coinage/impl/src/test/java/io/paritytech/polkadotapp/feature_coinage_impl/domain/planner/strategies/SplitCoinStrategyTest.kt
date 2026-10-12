package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinProvenance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.CoinageTransactionService
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransactionAssets
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission.TransferSubmissionParams
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
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
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SplitCoinStrategyTest {
    private val transactionService: CoinageTransactionService = mock(CoinageTransactionService::class.java)

    private val strategy = SplitCoinStrategy(
        transactionService = transactionService,
        payload = StrategyType.Split(
            splitFrom = coin(index = 1, exponent = 3),
            recipientDenominations = listOf(ValueExponent(2)),
            changeDenominations = listOf(ValueExponent(2)),
            exactCoins = emptyList(),
        ),
        coinageTransactionFactory = HeldMintTransactionFactory(),
    )

    @Test
    fun `a mint cancelled while it waits is a cancellation, not a failed result`() = runBlocking<Unit> {
        var result: Result<ScheduledTransfer>? = null
        val scheduling = launch { result = with(StalenessReportCollector.NoOp) { strategy.schedule(PARAMS) } }
        yield()

        scheduling.cancelAndJoin()

        assertTrue("the scheduling was not cancelled", scheduling.isCancelled)
        assertNull("a cancelled scheduling produced a result", result)
        verify(transactionService, never()).preCommitHandoff(any())
    }

    private fun coin(index: Int, exponent: Int) = Coin(
        derivationIndex = testKey(index),
        valueExponent = ValueExponent(exponent),
        age = Coin.Age.Known(0),
        isOnChain = true,
        accountId = byteArrayOf(index.toByte()).intoAccountId(),
        provenance = CoinProvenance.UNKNOWN,
    )

    private class HeldMintTransactionFactory : CoinageTransaction.Factory {
        override fun newTransaction(): CoinageTransaction = HeldMintTransaction()
    }

    private class HeldMintTransaction : CoinageTransaction {
        override suspend fun mintCoins(valueExponents: List<ValueExponent>, provenance: CoinProvenance): Result<List<Coin>> =
            awaitCancellation()

        override suspend fun mintVoucher(valueExponent: ValueExponent): Result<RecyclerVoucher> = awaitCancellation()

        override fun consumeCoins(coins: List<Coin>) = Unit

        override fun consumeReceivedCoin(publicKey: DataByteArray) = Unit

        override fun useVouchers(vouchers: List<RecyclerVoucher>) = Unit

        override fun handOff(assets: List<OwnAsset>) = Unit

        override fun build() = CoinageTransactionAssets(inputs = emptyList(), outputs = emptyList(), handedOff = emptyList())
    }

    private companion object {
        val PARAMS = TransferSubmissionParams(buildUntil = Instant.DISTANT_FUTURE, retryFailures = false)
    }
}
