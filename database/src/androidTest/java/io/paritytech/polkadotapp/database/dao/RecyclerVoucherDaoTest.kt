package io.paritytech.polkadotapp.database.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.paritytech.polkadotapp.database.AppDatabase
import io.paritytech.polkadotapp.database.model.RecyclerVoucherLocal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecyclerVoucherDaoTest {
    private lateinit var database: AppDatabase
    private val publicKey = byteArrayOf(7)

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
    fun repeatedObservationsKeepTheFirstConfirmedInclusionTime() = runBlocking {
        dao().insert(voucher(null, null, null))
        dao().updateLocation(publicKey, 2, 31, 1_000L)
        dao().updateLocation(publicKey, 2, 32, 601_000L)

        assertEquals(listOf(2L, 32L, 1_000L), storedLocation())
    }

    @Test
    fun movingToAnotherRingStartsANewTimer() = runBlocking {
        dao().insert(voucher(2, 32, 1_000L))
        dao().updateLocation(publicKey, 3, 40, 601_000L)

        assertEquals(listOf(3L, 40L, 601_000L), storedLocation())
    }

    @Test
    fun restoreRestartsTheTimerAtTheNextConfirmedObservation() = runBlocking {
        dao().insert(voucher(2, 32, 1_000L))
        dao().insertAll(listOf(voucher(2, 0, null)))
        assertEquals(listOf(2L, 0L, null), storedLocation())

        dao().updateLocation(publicKey, 2, 32, 61_000L)
        dao().updateLocation(publicKey, 2, 33, 121_000L)

        assertEquals(listOf(2L, 33L, 61_000L), storedLocation())
    }

    private fun dao() = database.recyclerVoucherDao()

    private fun voucher(ring: Int?, members: Int?, enteredAt: Long?) = RecyclerVoucherLocal(
        installationId = ByteArray(32),
        ringVrfKeyIndex = 1,
        ringVrfPublicKey = publicKey,
        recyclerValue = 3,
        locationRecyclerIndex = ring,
        recyclerMembers = members,
        enteredAt = enteredAt,
    )

    private suspend fun storedLocation(): List<Long?> = dao().getAllVouchers().single().let {
        listOf(it.locationRecyclerIndex?.toLong(), it.recyclerMembers?.toLong(), it.enteredAt)
    }
}
