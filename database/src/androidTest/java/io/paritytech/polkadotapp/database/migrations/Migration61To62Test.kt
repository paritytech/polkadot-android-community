package io.paritytech.polkadotapp.database.migrations

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.paritytech.polkadotapp.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration61To62Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun legacyVouchersKeepTheirLocationWithoutInventingAnInclusionTime() = runBlocking<Unit> {
        helper.createDatabase(TEST_DB, 61).apply {
            execSQL("INSERT INTO recycler_vouchers VALUES (1, X'07', 3, 2, 32)")
            execSQL("INSERT INTO recycler_vouchers VALUES (2, X'08', 4, NULL, NULL)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 62, true).close()

        val database = openDatabase()
        try {
            val vouchers = database.recyclerVoucherDao().getAllVouchers().sortedBy { it.ringVrfKeyIndex }
            assertEquals(
                listOf(listOf(1L, 7L, 3L, 2L, 32L, null), listOf(2L, 8L, 4L, null, null, null)),
                vouchers.map {
                    listOf(
                        it.ringVrfKeyIndex.toLong(), it.ringVrfPublicKey.single().toLong(), it.recyclerValue.toLong(),
                        it.locationRecyclerIndex?.toLong(), it.recyclerMembers?.toLong(), it.enteredAt
                    )
                }
            )
            database.recyclerVoucherDao().updateLocation(byteArrayOf(7), 2, 32, 1_000L)
        } finally {
            database.close()
        }
        val reopenedDatabase = openDatabase()
        try {
            assertEquals(1_000L, reopenedDatabase.recyclerVoucherDao().getByRingVrfKeyIndices(ByteArray(32), listOf(1)).single().enteredAt)
        } finally {
            reopenedDatabase.close()
        }
    }

    private fun openDatabase() = Room.databaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
        TEST_DB
    ).addMigrations(Migration62To63()).build()

    private companion object {
        const val TEST_DB = "voucher-migration-test"
    }
}
