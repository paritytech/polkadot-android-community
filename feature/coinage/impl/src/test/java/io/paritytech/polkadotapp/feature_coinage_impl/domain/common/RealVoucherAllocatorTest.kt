package io.paritytech.polkadotapp.feature_coinage_impl.domain.common

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchPublicKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageKeyIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.TEST_INSTALLATION
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.VoucherRingDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.ExponentBoundsRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.installation.CoinageInstallationGate
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.test_shared.assertFailure
import io.paritytech.polkadotapp.test_shared.assertSuccess
import io.paritytech.polkadotapp.test_shared.awaitCompletes
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.time.Duration.Companion.milliseconds

class RealVoucherAllocatorTest {
    private val voucherRepository: VoucherRepository = mock(VoucherRepository::class.java)
    private val voucherRingDerivation: VoucherRingDerivation = mock(VoucherRingDerivation::class.java)
    private val boundsRepository: ExponentBoundsRepository = mock(ExponentBoundsRepository::class.java)
    private val chainAssetProvider: ChainAssetProvider = mock(ChainAssetProvider::class.java)
    private val gate = FakeInstallationGate()

    private val allocator = RealVoucherAllocator(
        voucherRepository = voucherRepository,
        installationGate = gate,
        voucherRingDerivation = voucherRingDerivation,
        boundsRepository = boundsRepository,
        chainAssetProvider = chainAssetProvider,
    )

    @Before
    fun setUp() = runBlocking<Unit> {
        withChain()
        withExponentBounds(min = 0, max = 30)
        withNextIndex(NEXT_INDEX)
        withMemberKeys()
    }

    @Test
    fun `an allocated voucher takes the next index of the current installation`() = runBlocking<Unit> {
        gate.release()

        val voucher = allocator.allocate(ValueExponent(3)).getOrThrow()

        assertEquals(CoinageKeyIndex(TEST_INSTALLATION, NEXT_INDEX), voucher.ringVrfKeyIndex)
        assertEquals(FIRST_KEY, voucher.ringVrfPublicKey)
        assertEquals(ValueExponent(3), voucher.recyclerValue)
        verifyVoucherSaved(voucher)
    }

    @Test
    fun `a batch takes consecutive indices and is saved once`() = runBlocking<Unit> {
        gate.release()

        val vouchers = allocator.allocateAll(listOf(ValueExponent(1), ValueExponent(2))).getOrThrow()

        assertEquals(listOf(NEXT_INDEX, NEXT_INDEX + 1), vouchers.map { it.ringVrfKeyIndex.item })
        assertEquals(listOf(FIRST_KEY, SECOND_KEY), vouchers.map { it.ringVrfPublicKey })
        verifyBatchSaved(vouchers)
    }

    @Test
    fun `allocate waits for the installation gate before taking an index`() = runBlocking<Unit> {
        val allocation = async { allocator.allocate(ValueExponent(3)) }

        assertStillWaiting(allocation)
        verifyNoIndexTaken()

        gate.release()

        assertSuccess(awaitCompletes { allocation.await() })
    }

    @Test
    fun `allocateAll waits for the installation gate before taking an index`() = runBlocking<Unit> {
        val allocation = async { allocator.allocateAll(listOf(ValueExponent(3))) }

        assertStillWaiting(allocation)
        verifyNoIndexTaken()

        gate.release()

        assertSuccess(awaitCompletes { allocation.await() })
    }

    @Test
    fun `a cancelled wait is a cancellation, not a failed result`() = runBlocking<Unit> {
        var result: Result<RecyclerVoucher>? = null
        val allocation = launch { result = allocator.allocate(ValueExponent(3)) }
        yield()

        allocation.cancelAndJoin()

        assertTrue("the allocation was not cancelled", allocation.isCancelled)
        assertNull("a cancelled allocation produced a result", result)
    }

    @Test
    fun `a waiting allocation does not hold the allocation lock`() = runBlocking<Unit> {
        val held = async { allocator.allocate(ValueExponent(3)) }
        assertStillWaiting(held)
        gate.releaseNextCallOnly()

        val unblocked = async { allocator.allocate(ValueExponent(4)) }

        assertSuccess(awaitCompletes { unblocked.await() })
        assertStillWaiting(held)
        held.cancel()
    }

    @Test
    fun `a failing installation read is a failed result, not an exception`() = runBlocking<Unit> {
        gate.fail(IllegalStateException("installations table unavailable"))

        val result = allocator.allocate(ValueExponent(3))

        assertFailure(result)
        verifyNoIndexTaken()
    }

    @Test
    fun `an out-of-bounds exponent fails without consulting the gate`() = runBlocking<Unit> {
        val result = awaitCompletes { allocator.allocate(ValueExponent(99)) }

        assertFailure(result)
        assertEquals(0, gate.requests)
    }

    private fun withChain() {
        whenever(chainAssetProvider.chainId()).thenReturn(CHAIN)
    }

    private suspend fun withExponentBounds(min: Int, max: Int) {
        whenever(boundsRepository.minExponent(CHAIN)).thenReturn(Result.success(min))
        whenever(boundsRepository.maxExponent(CHAIN)).thenReturn(Result.success(max))
    }

    private suspend fun withNextIndex(index: Int) {
        whenever(voucherRepository.getNextDerivationIndex(TEST_INSTALLATION)).thenReturn(index)
    }

    private suspend fun withMemberKeys() {
        whenever(voucherRingDerivation.memberKeyOf(CoinageKeyIndex(TEST_INSTALLATION, NEXT_INDEX))).thenReturn(FIRST_KEY)
        whenever(voucherRingDerivation.memberKeyOf(CoinageKeyIndex(TEST_INSTALLATION, NEXT_INDEX + 1))).thenReturn(SECOND_KEY)
    }

    private suspend fun verifyVoucherSaved(voucher: RecyclerVoucher) {
        verify(voucherRepository).saveNew(voucher)
    }

    private suspend fun verifyBatchSaved(vouchers: List<RecyclerVoucher>) {
        verify(voucherRepository).saveNew(vouchers)
    }

    private suspend fun verifyNoIndexTaken() {
        verify(voucherRepository, never()).getNextDerivationIndex(TEST_INSTALLATION)
    }

    // Real time, because runBlocking has none to skip: a bounded wait shorter than the probe fails here.
    private suspend fun assertStillWaiting(allocation: Deferred<*>) {
        yield()
        delay(WAIT_PROBE)
        assertFalse("the allocation completed while the gate was closed", allocation.isCompleted)
    }

    private class FakeInstallationGate : CoinageInstallationGate {
        var requests = 0
            private set

        private var release = CompletableDeferred<CoinageInstallationId>()
        private var failure: Throwable? = null
        private var singleRelease = false

        fun release() {
            release.complete(TEST_INSTALLATION)
        }

        fun releaseNextCallOnly() {
            singleRelease = true
        }

        fun fail(cause: Throwable) {
            failure = cause
        }

        override suspend fun awaitCurrent(): CoinageInstallationId {
            requests++
            failure?.let { throw it }

            if (singleRelease) {
                singleRelease = false
                return TEST_INSTALLATION
            }

            return release.await()
        }

        override fun subscribeOnHold(): Flow<Boolean> = flowOf(false)
    }

    private companion object {
        const val CHAIN = "people"
        const val NEXT_INDEX = 41
        val WAIT_PROBE = 200.milliseconds
        val FIRST_KEY: BandersnatchPublicKey = ByteArray(32) { 7 }.toDataByteArray()
        val SECOND_KEY: BandersnatchPublicKey = ByteArray(32) { 8 }.toDataByteArray()
    }
}
