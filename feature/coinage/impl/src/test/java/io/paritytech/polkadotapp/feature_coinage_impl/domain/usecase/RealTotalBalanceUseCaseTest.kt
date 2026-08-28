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
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageAssetState
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageTransactionStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageAssetsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedVoucher
import io.paritytech.polkadotapp.feature_coinage_impl.common.testConversionContext
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigInteger

class RealTotalBalanceUseCaseTest {
    private val coinRepository: CoinRepository
    private val coinageAssetsUseCase: CoinageAssetsUseCase
    private val coinageBalanceConverterUseCase: CoinageBalanceConverterUseCase
    private val useCase: RealTotalBalanceUseCase

    private val maxAge = 10
    private val timestamp = 1000L
    private val voucherSpendableTimestamp = timestamp - 1L
    private val voucherPendingTimestamp = timestamp + 1L

    init {
        coinRepository = mock()
        coinageAssetsUseCase = mock()
        coinageBalanceConverterUseCase = mock()

        runBlocking {
            `when`(coinageBalanceConverterUseCase.create()).thenReturn(Result.success(testConversionContext))
        }

        useCase = RealTotalBalanceUseCase(coinRepository, coinageAssetsUseCase, coinageBalanceConverterUseCase)
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
            coins = listOf(createCoin(age = Coin.Age.Known(maxAge - 1), onChain = true, exponent = 1)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(secured = 1.exponentToBalance()),
        )
    }

    /**
     * A coin the peer has taken keeps the last age it was seen with, so it still looks young enough to
     * spend. What makes it unspendable is that the chain no longer holds it.
     *
     * Only the age used to be checked, because presence was the same value. Now it is not, and counting
     * this coin would offer money that is already gone.
     */
    @Test
    fun `coin gone from the chain is not spendable however young its last age`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Known(maxAge - 1), onChain = false, exponent = 1)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(),
        )
    }

    /**
     * A coin handed to a peer before its mint finalized: absent, with a live minter, and no longer ours.
     *
     * It matches "on its way" on every count except the one that matters — the key has left, so whatever
     * arrives is not ours to spend.
     */
    @Test
    fun `coin handed off before its mint finalized is not pending`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Unknown, onChain = false, exponent = 1)),
            coinStates = listOf(
                CoinageAssetState(handedOff = true, minterStatus = CoinageTransactionStatus.PENDING, consumerStatus = null)
            ),
            vouchers = emptyList(),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `coin absent from chain with no minter of ours counts nowhere`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Unknown, onChain = false, exponent = 1)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `coin absent from chain is pending while the transaction minting it is live`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Unknown, onChain = false, exponent = 1)),
            coinStates = listOf(stateWithMinter(CoinageTransactionStatus.PENDING)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(pending = 1.exponentToBalance()),
        )
    }

    @Test
    fun `coin absent from chain counts nowhere once the transaction minting it failed`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Unknown, onChain = false, exponent = 1)),
            coinStates = listOf(stateWithMinter(CoinageTransactionStatus.FAILURE)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `coin held by a live transaction of ours counts nowhere`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Known(0), onChain = true, exponent = 1)),
            coinStates = listOf(stateWithConsumer(CoinageTransactionStatus.PENDING)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `handed off coin counts nowhere`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Known(0), onChain = true, exponent = 1)),
            coinStates = listOf(CoinageAssetState(handedOff = true, minterStatus = null, consumerStatus = null)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `coin with recycling age is pending`() {
        assertCalculatedBalance(
            coins = listOf(createCoin(age = Coin.Age.Known(maxAge), onChain = true, exponent = 1)),
            vouchers = emptyList(),
            expectedBalance = balanceOf(pending = 1.exponentToBalance()),
        )
    }

    @Test
    fun `coin is added to secured, never to degraded`() {
        // Two spendable coins with different exponents: both contribute to spendable.secured.
        assertCalculatedBalance(
            coins = listOf(
                createCoin(age = Coin.Age.Known(maxAge - 1), onChain = true, exponent = 1),
                createCoin(age = Coin.Age.Known(0), onChain = true, exponent = 2),
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
        // In recycler and past the unload delay, but enoughMembers=false
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
    fun `voucher is degraded when ready to use but unload delay not passed`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(
                createVoucher(
                    delayUnloadUntil = voucherPendingTimestamp,
                    location = Location.InRecycler(RecyclerIndex(BigInteger.ONE)),
                    enoughMembers = true,
                    exponent = 1,
                )
            ),
            expectedBalance = balanceOf(degraded = 1.exponentToBalance()),
        )
    }

    @Test
    fun `voucher with unknown location is pending while the transaction minting it is live`() {
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
            voucherStates = listOf(stateWithMinter(CoinageTransactionStatus.PENDING)),
            expectedBalance = balanceOf(
                pending = 1.exponentToBalance(),
            ),
        )
    }

    @Test
    fun `voucher with unknown location counts nowhere once the transaction minting it failed`() {
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
            voucherStates = listOf(stateWithMinter(CoinageTransactionStatus.FAILURE)),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `onboarding voucher is pending whatever the ledger says about its minter`() {
        // It is already registered on chain, so its minter succeeded whether or not we still hold the entry.
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
            expectedBalance = balanceOf(pending = 1.exponentToBalance()),
        )
    }

    @Test
    fun `calculates coins and vouchers correctly`() {
        val coins = listOf(
            // Spendable → secured.
            createCoin(age = Coin.Age.Known(maxAge - 1), onChain = true, exponent = 1),
            // Pending (at recycling age).
            createCoin(age = Coin.Age.Known(maxAge), onChain = true, exponent = 2),
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

    @Test
    fun `voucher held by a live transaction of ours counts nowhere`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(securedVoucher(exponent = 1)),
            voucherStates = listOf(stateWithConsumer(CoinageTransactionStatus.PENDING)),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `voucher a finalized transaction of ours already spent counts nowhere`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(securedVoucher(exponent = 1)),
            voucherStates = listOf(stateWithConsumer(CoinageTransactionStatus.FINALIZED_SUCCESS)),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `voucher a failed transaction of ours tried to spend counts again`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(securedVoucher(exponent = 1)),
            voucherStates = listOf(stateWithConsumer(CoinageTransactionStatus.FAILURE)),
            expectedBalance = balanceOf(secured = 1.exponentToBalance()),
        )
    }

    @Test
    fun `handed off voucher counts nowhere`() {
        assertCalculatedBalance(
            coins = emptyList(),
            vouchers = listOf(securedVoucher(exponent = 1)),
            voucherStates = listOf(CoinageAssetState(handedOff = true, minterStatus = null, consumerStatus = null)),
            expectedBalance = balanceOf(),
        )
    }

    @Test
    fun `voucher not in the recycler is pending while the transaction minting it is live`() {
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
            voucherStates = listOf(stateWithMinter(CoinageTransactionStatus.PENDING)),
            expectedBalance = balanceOf(pending = 1.exponentToBalance()),
        )
    }

    private fun securedVoucher(exponent: Int) = createVoucher(
        delayUnloadUntil = voucherSpendableTimestamp,
        location = Location.InRecycler(RecyclerIndex(BigInteger.ONE)),
        enoughMembers = true,
        exponent = exponent,
    )

    private fun stateWithMinter(status: CoinageTransactionStatus) =
        CoinageAssetState(handedOff = false, minterStatus = status, consumerStatus = null)

    private fun stateWithConsumer(status: CoinageTransactionStatus) =
        CoinageAssetState(handedOff = false, minterStatus = null, consumerStatus = status)

    private fun assertCalculatedBalance(
        coins: List<Coin>,
        vouchers: List<RecyclerVoucher>,
        expectedBalance: CoinageBalance,
        coinStates: List<CoinageAssetState> = coins.map { CoinageAssetState.UNTRACKED },
        voucherStates: List<CoinageAssetState> = vouchers.map { CoinageAssetState.UNTRACKED },
        recyclingAge: Int = maxAge,
        currentTimeMillis: Timestamp = timestamp,
    ) = runBlocking {
        val actualBalance = useCase.calculateCoinageBalance(
            coins = coins.zip(coinStates, ::TrackedCoin),
            recyclingAge = recyclingAge,
            vouchers = vouchers.zip(voucherStates, ::TrackedVoucher),
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

    /**
     * Presence is stated, not inferred from the age.
     *
     * They used to be one value, and a coin the chain no longer holds keeps the last age it was seen with —
     * so "known age, gone from chain" is a real state, and deriving one from the other makes it impossible
     * for a test to say.
     */
    private fun createCoin(age: Coin.Age, onChain: Boolean, exponent: Int): Coin {
        return Coin(
            derivationIndex = 0,
            valueExponent = ValueExponent(exponent),
            age = age,
            isOnChain = onChain,
            accountId = mock(),
        )
    }

    private fun createVoucher(
        delayUnloadUntil: Timestamp,
        location: Location,
        enoughMembers: Boolean,
        exponent: Int,
    ): RecyclerVoucher {
        return RecyclerVoucher(
            ringVrfKeyIndex = 0,
            ringVrfPublicKey = mock(),
            recyclerValue = ValueExponent(exponent),
            location = location,
            allocatedAt = 0L,
            delayUnloadUntil = delayUnloadUntil,
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
