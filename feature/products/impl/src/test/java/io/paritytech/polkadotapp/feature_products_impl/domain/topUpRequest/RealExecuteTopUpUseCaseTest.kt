package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ClaimReceivedCoinsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.OnboardingUseCase
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionSignerSource
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Claiming coins a product handed us as a top-up.
 *
 * Unlike a chat payment, something is blocked on this call, so the wait has to be bounded here: claiming
 * itself keeps going for as long as a coin is visible on chain, which is right for money sitting in a chat
 * and wrong for a host call a product is waiting on.
 */
@OptIn(ExperimentalTime::class)
class RealExecuteTopUpUseCaseTest {
    private val claimReceivedCoinsUseCase = FakeClaimReceivedCoinsUseCase()
    private val onboardingUseCase: OnboardingUseCase = mock()
    private val chainAssetProvider: ChainAssetProvider = mock()
    private val timeProvider = object : TimeProvider {
        override fun now(): Instant = NOW
    }

    private val useCase = RealExecuteTopUpUseCase(
        claimReceivedCoinsUseCase = claimReceivedCoinsUseCase,
        onboardingUseCase = onboardingUseCase,
        chainAssetProvider = chainAssetProvider,
        timeProvider = timeProvider,
    )

    @Test
    fun `Exact when onboard source claims successfully`() = runTest {
        withResolvedAsset()
        withOnboardingSucceeds()

        val result = useCase.claim(onboardSource(), 100.intoBalance())

        assertExact(result)
    }

    @Test
    fun `Exact when claimed coins match the stated amount`() = runTest {
        claimReceivedCoinsUseCase.emits(
            CoinageTransferDetection.Claiming,
            CoinageTransferDetection.Claimed(100.intoBalance(), finalized = true),
        )

        val result = useCase.claim(coinsSource(), 100.intoBalance())

        assertExact(result)
    }

    /** Claiming ran to the end and only part of the payment ever arrived. */
    @Test
    fun `Partial with the amount that landed when claiming ends short`() = runTest {
        claimReceivedCoinsUseCase.emits(
            CoinageTransferDetection.Claiming,
            CoinageTransferDetection.ClaimedPartially(80.intoBalance()),
        )

        val result = useCase.claim(coinsSource(), 100.intoBalance())

        assertPartial(result, expectedCredited = 80.intoBalance())
    }

    /**
     * Claiming is still going when the wait runs out — a coin is on chain and its claim keeps being
     * refused, which claiming will retry indefinitely.
     *
     * What already landed is still the user's, so it is reported rather than thrown away: calling a top-up
     * that moved 80 of 100 an outright failure would be worse than saying it was short.
     */
    @Test
    fun `Partial with what landed when the wait runs out mid-claim`() = runTest {
        claimReceivedCoinsUseCase.emitsThenHangs(CoinageTransferDetection.ClaimingRest(80.intoBalance()))

        val result = useCase.claim(coinsSource(), 100.intoBalance())

        assertPartial(result, expectedCredited = 80.intoBalance())
    }

    @Test
    fun `the first arrival decides, without waiting for finality`() = runTest {
        // Claiming keeps reporting to finality; a top-up that waited for it would hold open for tens of
        // seconds after the coins were already ours.
        claimReceivedCoinsUseCase.emitsThenHangs(
            CoinageTransferDetection.Claiming,
            CoinageTransferDetection.Claimed(100.intoBalance(), finalized = false),
        )

        val result = useCase.claim(coinsSource(), 100.intoBalance())

        assertExact(result)
    }

    @Test
    fun `failure when nothing was claimed`() = runTest {
        claimReceivedCoinsUseCase.emits(
            CoinageTransferDetection.Claiming,
            CoinageTransferDetection.NotClaimed,
        )

        val result = useCase.claim(coinsSource(), 100.intoBalance())

        assertFailure(result)
    }

    /** Claiming is allowed to retry for as long as the caller is prepared to wait, and no longer. */
    @Test
    fun `claiming is given the whole wait to finish`() = runTest {
        claimReceivedCoinsUseCase.emits(CoinageTransferDetection.Claimed(100.intoBalance(), finalized = true))

        useCase.claim(coinsSource(), 100.intoBalance())

        assertEquals(NOW + 60.seconds, claimReceivedCoinsUseCase.retryUntil)
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

    private companion object {
        val NOW: Instant = Instant.fromEpochSeconds(1_000)
    }
}

/** A fake rather than a mock: [ClaimReceivedCoinsUseCase.claim] takes a value class, which Mockito unwraps. */
@OptIn(ExperimentalTime::class)
private class FakeClaimReceivedCoinsUseCase : ClaimReceivedCoinsUseCase {
    private var states: List<CoinageTransferDetection> = emptyList()
    private var hangs = false

    var retryUntil: Instant? = null
        private set

    fun emits(vararg states: CoinageTransferDetection) {
        this.states = states.toList()
        hangs = false
    }

    /** Claiming that stays open after its last report, as it does whenever a coin is still claimable. */
    fun emitsThenHangs(vararg states: CoinageTransferDetection) {
        this.states = states.toList()
        hangs = true
    }

    override fun claim(
        coinKeys: List<CoinPrivateKey>,
        groupId: CoinageOperationGroupId,
        retryUntil: Instant,
    ): Flow<CoinageTransferDetection> {
        this.retryUntil = retryUntil

        return flow {
            states.forEach { emit(it) }
            if (hangs) awaitCancellation()
        }
    }
}
