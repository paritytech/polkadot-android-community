package io.paritytech.polkadotapp.feature_coinage_impl.domain.common

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinProvenance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageKeyIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.TEST_INSTALLATION
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.CoinKeypairDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.ExponentBoundsRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.installation.CoinageInstallationGate
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.test_shared.LONG_WAIT
import io.paritytech.polkadotapp.test_shared.assertFailure
import io.paritytech.polkadotapp.test_shared.assertSuccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealCoinAllocatorTest {
    private val coinRepository = mockk<CoinRepository>(relaxed = true)
    private val installationGate = mockk<CoinageInstallationGate>()
    private val boundsRepository = mockk<ExponentBoundsRepository>()

    private val allocator = RealCoinAllocator(
        coinRepository = coinRepository,
        installationGate = installationGate,
        keypairDerivation = mockk<CoinKeypairDerivation> {
            coEvery { deriveKeypair(any()) } returns mockk { every { publicKey } returns ByteArray(32) }
        },
        boundsRepository = boundsRepository,
        chainAssetProvider = mockk<ChainAssetProvider> { every { chainId() } returns CHAIN },
    )

    @Before
    fun setUp() {
        coEvery { boundsRepository.minExponent(CHAIN) } returns Result.success(0)
        coEvery { boundsRepository.maxExponent(CHAIN) } returns Result.success(30)
        coEvery { installationGate.awaitCurrent() } returns TEST_INSTALLATION
        coEvery { coinRepository.getNextDerivationIndex(TEST_INSTALLATION) } returns NEXT_INDEX
    }

    @Test
    fun `an allocated coin takes the next index of the current installation`() = runTest {
        val coin = allocator.allocate(ValueExponent(3), CoinProvenance.UNKNOWN).getOrThrow()

        assertEquals(CoinageKeyIndex(TEST_INSTALLATION, NEXT_INDEX), coin.derivationIndex)
        coVerify { coinRepository.saveNew(coin) }
    }

    @Test
    fun `an allocation whose index is already taken fails instead of overwriting the row`() = runTest {
        coEvery { coinRepository.saveNew(any<Coin>()) } throws IllegalStateException("UNIQUE constraint failed: coins")

        val result = allocator.allocate(ValueExponent(3), CoinProvenance.UNKNOWN)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { coinRepository.save(any()) }
    }

    @Test
    fun `a batch whose indices are already taken fails instead of overwriting the rows`() = runTest {
        coEvery { coinRepository.saveNew(any<List<Coin>>()) } throws IllegalStateException("UNIQUE constraint failed: coins")

        val result = allocator.allocateAll(listOf(ValueExponent(1), ValueExponent(2)), CoinProvenance.UNKNOWN)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { coinRepository.saveAll(any()) }
    }

    @Test
    fun `allocate waits for the installation gate, however long it takes, before taking an index`() = runTest {
        val release = givenGateHolds()

        val allocation = async { allocator.allocate(ValueExponent(3), CoinProvenance.UNKNOWN) }
        runCurrent()
        advanceTimeBy(LONG_WAIT)

        coVerify(exactly = 0) { coinRepository.getNextDerivationIndex(TEST_INSTALLATION) }
        assertFalse("the allocation completed while the gate was closed", allocation.isCompleted)

        release.complete(TEST_INSTALLATION)
        runCurrent()

        assertEquals(TEST_INSTALLATION, allocation.await().getOrThrow().derivationIndex.installation)
    }

    @Test
    fun `allocateAll waits for the installation gate before taking an index`() = runTest {
        val release = givenGateHolds()

        val allocation = async { allocator.allocateAll(listOf(ValueExponent(3)), CoinProvenance.UNKNOWN) }
        runCurrent()
        advanceTimeBy(LONG_WAIT)

        coVerify(exactly = 0) { coinRepository.getNextDerivationIndex(TEST_INSTALLATION) }
        assertFalse("the allocation completed while the gate was closed", allocation.isCompleted)

        release.complete(TEST_INSTALLATION)
        runCurrent()

        assertEquals(TEST_INSTALLATION, allocation.await().getOrThrow().single().derivationIndex.installation)
    }

    @Test
    fun `a cancelled wait is a cancellation, not a failed result`() = runTest {
        coEvery { installationGate.awaitCurrent() } coAnswers { awaitCancellation() }
        var result: Result<Coin>? = null
        val allocation = launch { result = allocator.allocate(ValueExponent(3), CoinProvenance.UNKNOWN) }
        runCurrent()

        allocation.cancelAndJoin()

        assertTrue("the allocation was not cancelled", allocation.isCancelled)
        assertNull("a cancelled allocation produced a result", result)
    }

    @Test
    fun `a waiting allocation does not hold the allocation lock`() = runTest {
        val release = CompletableDeferred<CoinageInstallationId>()
        var calls = 0
        coEvery { installationGate.awaitCurrent() } coAnswers { if (calls++ == 0) release.await() else TEST_INSTALLATION }

        val held = async { allocator.allocate(ValueExponent(3), CoinProvenance.UNKNOWN) }
        runCurrent()
        val unblocked = async { allocator.allocate(ValueExponent(4), CoinProvenance.UNKNOWN) }
        runCurrent()

        assertTrue("the second allocation queued behind the waiting one", unblocked.isCompleted)
        assertFalse("the waiting allocation completed while the gate was closed", held.isCompleted)

        release.complete(TEST_INSTALLATION)
        runCurrent()
        assertSuccess(held.await())
    }

    @Test
    fun `a failing installation read is a failed result, not an exception`() = runTest {
        coEvery { installationGate.awaitCurrent() } throws IllegalStateException("installations table unavailable")

        val result = allocator.allocate(ValueExponent(3), CoinProvenance.UNKNOWN)

        assertFailure(result)
        coVerify(exactly = 0) { coinRepository.getNextDerivationIndex(TEST_INSTALLATION) }
    }

    @Test
    fun `an out-of-bounds exponent fails without consulting the gate`() = runTest {
        val result = allocator.allocate(ValueExponent(99), CoinProvenance.UNKNOWN)

        assertFailure(result)
        coVerify(exactly = 0) { installationGate.awaitCurrent() }
    }

    private fun givenGateHolds(): CompletableDeferred<CoinageInstallationId> {
        val release = CompletableDeferred<CoinageInstallationId>()
        coEvery { installationGate.awaitCurrent() } coAnswers { release.await() }
        return release
    }

    private companion object {
        const val CHAIN = "people"
        const val NEXT_INDEX = 41
    }
}
