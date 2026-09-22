package io.paritytech.polkadotapp.feature_coinage_impl.data.transaction

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.database.dao.CoinageEntryDao
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageRegistrationError
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealCoinageAssetLedgerTest {
    private val dao = mockk<CoinageEntryDao>(relaxed = true)

    private val ledger = RealCoinageAssetLedger(dao)

    @Before
    fun setUp() {
        coEvery { dao.withTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
        coEvery { dao.filterClaimed(any()) } returns emptyList()
        coEvery { dao.filterHandedOff(any()) } returns emptyList()
    }

    @Test
    fun `an asset that already carries a handoff mark cannot be handed off again`() = runTest {
        coEvery { dao.filterHandedOff(any()) } returns listOf(KEY)

        val error = ledger.markHandedOff(listOf(MARK)).exceptionOrNull()

        assertTrue("expected a handed-off rejection, got $error", error is CoinageRegistrationError.HandoffOfHandedOffAsset)
        assertEquals(COIN, (error as CoinageRegistrationError.HandoffOfHandedOffAsset).asset)
        coVerify(exactly = 0) { dao.insertHandoffs(any()) }
    }

    @Test
    fun `an unclaimed and unmarked asset is marked handed off`() = runTest {
        val result = ledger.markHandedOff(listOf(MARK))

        assertTrue(result.isSuccess)
        coVerify { dao.insertHandoffs(match { row -> row.single().onChainKey contentEquals KEY }) }
    }

    private companion object {
        val KEY = ByteArray(32) { 0x11 }
        val COIN = OwnAsset.Coin(testKey(1))
        val MARK = LedgerAsset(CoinageAssetKind.COIN, COIN, KEY.toDataByteArray())
    }
}
