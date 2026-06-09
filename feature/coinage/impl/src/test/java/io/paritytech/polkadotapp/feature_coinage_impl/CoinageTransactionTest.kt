package io.paritytech.polkadotapp.feature_coinage_impl

import io.paritytech.polkadotapp.common.utils.emptySubstrateAccountId
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.VoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher.Location
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction.Stage
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.RealCoinageTransaction
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigInteger

class CoinageTransactionTest {
    private val coinAllocator: CoinAllocator = mock()
    private val voucherAllocator: VoucherAllocator = mock()
    private val coinRepository: CoinRepository = mock()
    private val voucherRepository: VoucherRepository = mock()

    private fun newTransaction() = RealCoinageTransaction(coinAllocator, voucherAllocator, coinRepository, voucherRepository)

    @Test
    fun `minted coins are removed on rollback`() = runBlocking {
        whenever(coinAllocator.allocateAll(any())).thenReturn(Result.success(listOf(coin(1, 2), coin(2, 3))))

        val transaction = newTransaction()
        transaction.mintCoins(listOf(ValueExponent(2), ValueExponent(3)))
        transaction.rollback(Stage.MEMO_SHARED, RuntimeException("boom"))

        verify(coinAllocator).deallocate(listOf(1, 2))
    }

    @Test
    fun `minted coins are untouched on commit`() = runBlocking {
        whenever(coinAllocator.allocateAll(any())).thenReturn(Result.success(listOf(coin(1, 2))))

        val transaction = newTransaction()
        transaction.mintCoins(listOf(ValueExponent(2)))
        transaction.commit()

        verify(coinAllocator, never()).deallocate(any())
        verify(coinRepository, never()).setSpentStateByDerivationIndices(any(), any())
    }

    @Test
    fun `consumed coin is marked spent locally on apply and on chain on commit`() = runBlocking {
        val transaction = newTransaction()
        transaction.consumeCoins(listOf(coin(7, 4)))
        verify(coinRepository).setSpentStateByDerivationIndices(listOf(7), Coin.SpentState.SPENT_LOCALLY)

        transaction.commit()
        verify(coinRepository).setSpentStateByDerivationIndices(listOf(7), Coin.SpentState.SPENT_ON_CHAIN)
    }

    @Test
    fun `consumed coin reverts to not spent on rollback`() = runBlocking {
        val transaction = newTransaction()
        transaction.consumeCoins(listOf(coin(7, 4)))
        transaction.rollback(Stage.MEMO_SHARED, RuntimeException("boom"))

        verify(coinRepository).setSpentStateByDerivationIndices(listOf(7), Coin.SpentState.NOT_SPENT)
    }

    @Test
    fun `handed-off coin keeps its spend once the memo is shared`() = runBlocking {
        val transaction = newTransaction()
        transaction.handOffCoins(listOf(coin(9, 1)))
        transaction.rollback(Stage.MEMO_SHARED, RuntimeException("boom"))

        verify(coinRepository).setSpentStateByDerivationIndices(listOf(9), Coin.SpentState.SPENT_LOCALLY)
        verify(coinRepository, never()).setSpentStateByDerivationIndices(listOf(9), Coin.SpentState.NOT_SPENT)
    }

    @Test
    fun `handed-off coin reverts when preparation fails before the memo is shared`() = runBlocking {
        val transaction = newTransaction()
        transaction.handOffCoins(listOf(coin(9, 1)))
        transaction.rollback(Stage.PREPARATION, RuntimeException("boom"))

        verify(coinRepository).setSpentStateByDerivationIndices(listOf(9), Coin.SpentState.NOT_SPENT)
    }

    @Test
    fun `used vouchers are marked used locally on apply and on chain on commit`() = runBlocking {
        val transaction = newTransaction()
        transaction.useVouchers(listOf(voucher(5)))
        verify(voucherRepository).setUsageStateByRingVrfKeyIndices(listOf(5), RecyclerVoucher.UsageState.USED_LOCALLY)

        transaction.commit()
        verify(voucherRepository).setUsageStateByRingVrfKeyIndices(listOf(5), RecyclerVoucher.UsageState.USED_ON_CHAIN)
    }

    @Test
    fun `used vouchers revert to not used on rollback`() = runBlocking {
        val transaction = newTransaction()
        transaction.useVouchers(listOf(voucher(5)))
        transaction.rollback(Stage.MEMO_SHARED, RuntimeException("boom"))

        verify(voucherRepository).setUsageStateByRingVrfKeyIndices(listOf(5), RecyclerVoucher.UsageState.NOT_USED)
    }

    @Test
    fun `minted voucher is deallocated on rollback`() = runBlocking {
        // ValueExponent is a value class — any() can't match it, so stub the concrete argument.
        whenever(voucherAllocator.allocate(ValueExponent(3))).thenReturn(Result.success(voucher(11)))

        val transaction = newTransaction()
        transaction.mintVoucher(ValueExponent(3))
        transaction.rollback(Stage.MEMO_SHARED, RuntimeException("boom"))

        verify(voucherAllocator).deallocate(listOf(11))
    }

    @Test(expected = IllegalStateException::class)
    fun `mutating after the transaction concluded is prohibited`(): Unit = runBlocking {
        val transaction = newTransaction()
        transaction.consumeCoins(listOf(coin(7, 4)))
        transaction.commit()

        transaction.consumeCoins(listOf(coin(8, 4)))
    }

    private fun coin(index: Int, exponent: Int) = Coin(
        derivationIndex = index,
        valueExponent = ValueExponent(exponent),
        age = Coin.Age.Known(0),
        spentState = Coin.SpentState.NOT_SPENT,
        accountId = emptySubstrateAccountId()
    )

    private fun voucher(index: Int) = RecyclerVoucher(
        ringVrfKeyIndex = index,
        ringVrfPublicKey = mock(),
        recyclerValue = ValueExponent(1),
        location = Location.InRecycler(RingIndex(BigInteger.ZERO)),
        allocatedAt = 0L,
        delayUnloadUntil = 0L,
        usageState = RecyclerVoucher.UsageState.NOT_USED,
        ringHasEnoughRingMembersToWithdraw = true
    )
}
