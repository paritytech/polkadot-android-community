package io.paritytech.polkadotapp.database.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.paritytech.polkadotapp.database.AppDatabase
import io.paritytech.polkadotapp.database.model.RecyclerVoucherLocal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A voucher's frozen maximum fungibility is written exactly once, and its current one may be left alone.
 *
 * Both guarantees live entirely in one SQL statement, which is why they are pinned here. The maximum records
 * the anonymity a ring offered when the voucher arrived; it is what makes a later, higher current fungibility
 * legible as the overloaded state the UI absorbs rather than as corruption. If it could move, that reading
 * would be gone. And a null parameter has to leave a stored fungibility standing, because the value needs
 * two chain reads that can fail independently of the location written alongside it.
 */
@RunWith(AndroidJUnit4::class)
class RecyclerVoucherDaoTest {

    private val publicKey = byteArrayOf(0x09)

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun theFirstUpdateFreezesTheMaximum() = runBlocking {
        givenVoucherOutsideRing()

        update(recyclerFungibility = 90, maxRecyclerFungibility = 90)

        assertEquals(90, storedVoucher().maxRecyclerFungibility)
    }

    @Test
    fun alaterUpdateLeavesTheFrozenMaximumAlone() = runBlocking {
        givenVoucherOutsideRing()

        update(recyclerFungibility = 90, maxRecyclerFungibility = 90)
        update(recyclerFungibility = 40, maxRecyclerFungibility = 40)

        val voucher = storedVoucher()
        assertEquals("the maximum is history and may not move", 90, voucher.maxRecyclerFungibility)
        assertEquals("the current value tracks the ring", 40, voucher.recyclerFungibility)
    }

    /**
     * The regression this test exists for. A ring already fully drained when the voucher landed in it has a
     * genuine maximum of zero, so zero must not be mistaken for "never frozen" — otherwise a later tick
     * overwrites it and the voucher appears to have had anonymity it never did.
     */
    @Test
    fun afrozenMaximumOfZeroIsStillFrozen() = runBlocking {
        givenVoucherOutsideRing()

        update(recyclerFungibility = 0, maxRecyclerFungibility = 0)
        update(recyclerFungibility = 70, maxRecyclerFungibility = 70)

        assertEquals("zero is a value, not an absence", 0, storedVoucher().maxRecyclerFungibility)
    }

    @Test
    fun aVoucherThatHasNeverBeenInARingHasNoMaximum() = runBlocking {
        givenVoucherOutsideRing()

        assertNull(storedVoucher().maxRecyclerFungibility)
    }

    @Test
    fun anUnreadableFungibilityLeavesTheStoredValuesStanding() = runBlocking {
        givenVoucherOutsideRing()
        update(recyclerFungibility = 55, maxRecyclerFungibility = 55)

        update(recyclerFungibility = null, maxRecyclerFungibility = null)

        val voucher = storedVoucher()
        assertEquals(55, voucher.recyclerFungibility)
        assertEquals(55, voucher.maxRecyclerFungibility)
    }

    /** A failed fungibility read must never cost the voucher the member count that decides spendability. */
    @Test
    fun anUnreadableFungibilityStillRecordsTheLocation() = runBlocking {
        givenVoucherOutsideRing()

        update(recyclerFungibility = null, maxRecyclerFungibility = null, recyclerMembers = 512)

        val voucher = storedVoucher()
        assertEquals(512, voucher.recyclerMembers)
        assertEquals(RING_INDEX, voucher.locationRecyclerIndex)
    }

    private fun dao() = database.recyclerVoucherDao()

    private suspend fun givenVoucherOutsideRing() {
        dao().insert(
            RecyclerVoucherLocal(
                ringVrfKeyIndex = 0,
                ringVrfPublicKey = publicKey,
                recyclerValue = 3,
                locationRecyclerIndex = null,
                recyclerMembers = null,
                recyclerFungibility = 0,
                maxRecyclerFungibility = null
            )
        )
    }

    private suspend fun update(
        recyclerFungibility: Int?,
        maxRecyclerFungibility: Int?,
        recyclerMembers: Int = 400
    ) = dao().updateLocation(
        ringVrfPublicKey = publicKey,
        recyclerIndex = RING_INDEX,
        recyclerMembers = recyclerMembers,
        recyclerFungibility = recyclerFungibility,
        maxRecyclerFungibility = maxRecyclerFungibility
    )

    private suspend fun storedVoucher(): RecyclerVoucherLocal =
        dao().getByRingVrfKeyIndices(listOf(0)).single()

    private companion object {
        const val RING_INDEX = 3
    }
}
