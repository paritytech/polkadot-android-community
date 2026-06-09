package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.formatExponentsToBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher.Location
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.common.testConversionContext
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigInteger

class RealTotalBalanceUseCaseTest {
    private val coinRepository: CoinRepository
    private val voucherRepository: VoucherRepository
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase
    private val useCase: RealTotalBalanceUseCase

    private val maxAge = 10
    private val timestamp = 1000L
    private val voucherSpendableTimestamp = timestamp - 1L
    private val voucherPendingTimestamp = timestamp + 1L

    init {
        coinRepository = mock()
        voucherRepository = mock()
        coinageBalanceConverterUseCase = mock()

        runBlocking {
            `when`(coinageBalanceConverterUseCase.create()).thenReturn(Result.success(testConversionContext))
        }

        useCase = RealTotalBalanceUseCase(coinRepository, voucherRepository, coinageBalanceConverterUseCase)
    }

    @Test
    fun `empty data returns zero balance`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = emptyList(),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `coin with lower age than recycling age is spendable`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Known(maxAge - 1), exponent = 1)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(secured = 1.exponentToBalance()),
        )
    }

    @Test
    fun `coin with unknown age is not spendable`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Unknown, exponent = 1)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(pending = 1.exponentToBalance()),
        )
    }

    @Test
    fun `coin with recycling age is pending`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Known(maxAge), exponent = 1)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(pending = 1.exponentToBalance()),
        )
    }

    @Test
    fun `coin is added to secured, never to degraded`() {
        // Two spendable coins with different exponents: both contribute to spendable.secured.
        assertCalculatedBalance(
            coins = listOf(
                createCoin(age = Coin.Age.Known(maxAge - 1), exponent = 1),
                createCoin(age = Coin.Age.Known(0), exponent = 2),
            ),
            vouchers = emptyList(),
            expectedBalance = balanceOf(secured = listOf(1, 2).exponentsToBalance()),
        )
    }

    @Test
    fun `voucher is secured with passed delay, in recycler and enough members`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(
                createVoucher(
                    delayUnloadUntil = voucherSpendableTimestamp,
                    location = Location.InRecycler(RecyclerIndex(BigInteger.ONE)),
                    enoughMembers = true,
                    exponent = 1,
                )
            ),
            expectedBalance = balanceOf(secured = 1.exponentToBalance()),
        )
    }

    @Test
    fun `voucher is degraded when ready to use but not enough ring members`() {
        // isReadyToUse=true (delay passed, in recycler, NOT_USED) but enoughMembers=false
        // → goes to degraded bucket, not pending, no latestUnload contribution.
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(
                createVoucher(
                    delayUnloadUntil = voucherSpendableTimestamp,
                    location = Location.InRecycler(RecyclerIndex(BigInteger.ONE)),
                    enoughMembers = false,
                    exponent = 1,
                )
            ),
            expectedBalance = balanceOf(degraded = 1.exponentToBalance()),
        )
    }

    @Test
    fun `voucher is pending when delay has not passed`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(
                createVoucher(
                    delayUnloadUntil = voucherPendingTimestamp,
                    location = Location.Onboarding,
                    enoughMembers = true,
                    exponent = 1,
                )
            ),
            expectedBalance = balanceOf(
                pending = 1.exponentToBalance(),
            ),
        )
    }

    @Test
    fun `voucher is pending with unknown location`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(
                createVoucher(
                    delayUnloadUntil = voucherSpendableTimestamp,
                    location = Location.Unknown,
                    enoughMembers = true,
                    exponent = 1,
                )
            ),
            expectedBalance = balanceOf(
                pending = 1.exponentToBalance(),
            ),
        )
    }

    @Test
    fun `voucher used locally is pending`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(
                createVoucher(
                    delayUnloadUntil = voucherSpendableTimestamp,
                    location = Location.InRecycler(RecyclerIndex(BigInteger.ONE)),
                    enoughMembers = true,
                    usageState = RecyclerVoucher.UsageState.USED_LOCALLY,
                    exponent = 1,
                )
            ),
            expectedBalance = balanceOf(
                pending = 1.exponentToBalance(),
            ),
        )
    }

    @Test
    fun `voucher used on chain is pending`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(
                createVoucher(
                    delayUnloadUntil = voucherSpendableTimestamp,
                    location = Location.InRecycler(RecyclerIndex(BigInteger.ONE)),
                    enoughMembers = true,
                    usageState = RecyclerVoucher.UsageState.USED_ON_CHAIN,
                    exponent = 1,
                )
            ),
            expectedBalance = balanceOf(
                pending = 1.exponentToBalance(),
            ),
        )
    }

    @Test
    fun `voucher is pending when in onboarding location`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(
                createVoucher(
                    delayUnloadUntil = voucherSpendableTimestamp,
                    location = Location.Onboarding,
                    enoughMembers = true,
                    exponent = 1,
                )
            ),
            expectedBalance = balanceOf(
                pending = 1.exponentToBalance(),
            ),
        )
    }

    @Test
    fun `calculates coins and vouchers correctly`() {
        val coins = listOf(
            // Spendable → secured.
            createCoin(age = Coin.Age.Known(maxAge - 1), exponent = 1),
            // Pending (at recycling age).
            createCoin(age = Coin.Age.Known(maxAge), exponent = 2),
        )

        val vouchers = listOf(
            // Pending (delay not passed).
            createVoucher(
                delayUnloadUntil = voucherPendingTimestamp,
                location = Location.InRecycler(RecyclerIndex(BigInteger.ONE)),
                enoughMembers = true,
                exponent = 3,
            ),
            // Secured (delay passed, in recycler, enough members).
            createVoucher(
                delayUnloadUntil = voucherSpendableTimestamp,
                location = Location.InRecycler(RecyclerIndex(BigInteger.ONE)),
                enoughMembers = true,
                exponent = 4,
            ),
            // Degraded (delay passed, in recycler, NOT enough members).
            createVoucher(
                delayUnloadUntil = voucherSpendableTimestamp,
                location = Location.InRecycler(RecyclerIndex(BigInteger.ONE)),
                enoughMembers = false,
                exponent = 5,
            ),
        )

        assertCalculatedBalance(
            coins = coins,
            vouchers = vouchers,
            expectedBalance = balanceOf(
                secured = listOf(1, 4).exponentsToBalance(),
                degraded = listOf(3, 5).exponentsToBalance(),
                pending = 2.exponentToBalance(),
            ),
        )
    }

    private fun assertCalculatedBalance(
        coins: List<Coin>,
        vouchers: List<RecyclerVoucher>,
        expectedBalance: CoinageBalance,
        recyclingAge: Int = maxAge,
        currentTimeMillis: Timestamp = timestamp,
    ) = runBlocking {
        val actualBalance = useCase.calculateCoinageBalance(
            coins = coins,
            recyclingAge = recyclingAge,
            vouchers = vouchers,
            currentTimeMillis = currentTimeMillis,
        ).getOrThrow()

        assertEquals(expectedBalance, actualBalance)
    }

    private fun balanceOf(
        secured: Balance = ZERO_BALANCE,
        degraded: Balance = ZERO_BALANCE,
        pending: Balance = ZERO_BALANCE,
    ): CoinageBalance = CoinageBalance(
        spendableBalance = CoinageBalance.SpendableBalance(
            degraded = degraded,
            secured = secured,
        ),
        pendingBalance = pending
    )

    private fun createCoin(age: Coin.Age, exponent: Int): Coin {
        return Coin(
            derivationIndex = 0,
            valueExponent = ValueExponent(exponent),
            age = age,
            spentState = Coin.SpentState.NOT_SPENT,
            accountId = mock(),
        )
    }

    private fun createVoucher(
        delayUnloadUntil: Timestamp,
        location: Location,
        enoughMembers: Boolean,
        exponent: Int,
        usageState: RecyclerVoucher.UsageState = RecyclerVoucher.UsageState.NOT_USED,
    ): RecyclerVoucher {
        return RecyclerVoucher(
            ringVrfKeyIndex = 0,
            ringVrfPublicKey = mock(),
            recyclerValue = ValueExponent(exponent),
            location = location,
            allocatedAt = 0L,
            delayUnloadUntil = delayUnloadUntil,
            usageState = usageState,
            ringHasEnoughRingMembersToWithdraw = enoughMembers,
        )
    }

    private fun Int.exponentToBalance(): Balance {
        return testConversionContext.formatExponentToBalance(ValueExponent(this))
    }

    private fun List<Int>.exponentsToBalance(): Balance {
        val exponents = this.map { ValueExponent(it) }
        return testConversionContext.formatExponentsToBalance(exponents)
    }

    companion object {
        private val ZERO_BALANCE: Balance = BigInteger.ZERO.intoBalance()
    }
}
