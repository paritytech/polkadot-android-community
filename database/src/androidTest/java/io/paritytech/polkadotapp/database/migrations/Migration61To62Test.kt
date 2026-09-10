package io.paritytech.polkadotapp.database.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.paritytech.polkadotapp.database.AppDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Own-asset rows gaining the installation their keys are derived under, with rows in the tables.
 *
 * Every existing key was derived under the `//0` page, so every existing own row must land on 32 zero bytes —
 * anything else would silently re-point a row at a different key.
 */
@RunWith(AndroidJUnit4::class)
class Migration61To62Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private lateinit var db: SupportSQLiteDatabase
    private lateinit var migratedDb: SupportSQLiteDatabase

    @Before
    fun setup() {
        db = helper.createDatabase(TEST_DB, 61)
    }

    @After
    fun tearDown() {
        if (::migratedDb.isInitialized) migratedDb.close()
    }

    @Test
    fun coinsKeepEveryColumnUnderTheLegacyPage() {
        db.insert(
            "coins",
            ContentValues().apply {
                put("derivationIndex", 5)
                put("accountId", byteArrayOf(0x05))
                put("valueExponent", 3)
                put("ageValue", 9)
                put("onChain", 1)
            }
        )

        val row = migrate().query("SELECT installationId, derivationIndex, accountId, valueExponent, ageValue, onChain FROM coins")

        assertTrue(row.moveToFirst())
        assertArrayEquals(LEGACY_ZERO, row.getBlob(0))
        assertEquals(5, row.getInt(1))
        assertArrayEquals(byteArrayOf(0x05), row.getBlob(2))
        assertEquals(3, row.getInt(3))
        assertEquals(9, row.getInt(4))
        assertEquals(1, row.getInt(5))
        row.close()
    }

    @Test
    fun recyclerVouchersKeepEveryColumnUnderTheLegacyPage() {
        db.insert(
            "recycler_vouchers",
            ContentValues().apply {
                put("ringVrfKeyIndex", 2)
                put("ringVrfPublicKey", byteArrayOf(0x02))
                put("recyclerValue", 4)
                put("locationRecyclerIndex", 1)
                put("recyclerMembers", 12)
            }
        )

        val row = migrate().query(
            "SELECT installationId, ringVrfKeyIndex, ringVrfPublicKey, recyclerValue, locationRecyclerIndex, recyclerMembers FROM recycler_vouchers"
        )

        assertTrue(row.moveToFirst())
        assertArrayEquals(LEGACY_ZERO, row.getBlob(0))
        assertEquals(2, row.getInt(1))
        assertArrayEquals(byteArrayOf(0x02), row.getBlob(2))
        assertEquals(4, row.getInt(3))
        assertEquals(1, row.getInt(4))
        assertEquals(12, row.getInt(5))
        row.close()
    }

    @Test
    fun ownInputsGetTheLegacyPageWhileReceivedInputsStayUnowned() {
        db.insertInput(entryId = 1, position = 0, derivationIndex = 4, key = byteArrayOf(0x04))
        db.insertInput(entryId = 1, position = 1, derivationIndex = null, key = byteArrayOf(0x7F))

        val rows = migrate().query("SELECT position, installationId, derivationIndex FROM coinage_entry_input ORDER BY position")

        assertTrue(rows.moveToFirst())
        assertArrayEquals(LEGACY_ZERO, rows.getBlob(1))
        assertEquals(4, rows.getInt(2))

        assertTrue(rows.moveToNext())
        assertNull(rows.getBlob(1))
        assertTrue(rows.isNull(2))
        rows.close()
    }

    @Test
    fun outputsAndHandoffsGetTheLegacyPage() {
        db.insert(
            "coinage_entry_output",
            ContentValues().apply {
                put("entryId", 1)
                put("position", 0)
                put("assetKind", "COIN")
                put("derivationIndex", 6)
                put("onChainKey", byteArrayOf(0x06))
            }
        )
        db.insert(
            "coinage_handoff",
            ContentValues().apply {
                put("onChainKey", byteArrayOf(0x08))
                put("assetKind", "COIN")
                put("derivationIndex", 8)
                put("committed", 1)
            }
        )

        val migrated = migrate()

        assertArrayEquals(LEGACY_ZERO, migrated.blob("SELECT installationId FROM coinage_entry_output WHERE derivationIndex = 6"))
        assertArrayEquals(LEGACY_ZERO, migrated.blob("SELECT installationId FROM coinage_handoff WHERE derivationIndex = 8"))
    }

    @Test
    fun theInstallationsTableStartsEmpty() {
        val cursor = migrate().query("SELECT COUNT(*) FROM coinage_installations")

        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        cursor.close()
    }

    // ---- fixtures ----

    private fun SupportSQLiteDatabase.insert(table: String, values: ContentValues) {
        insert(table, SQLiteDatabase.CONFLICT_NONE, values)
    }

    private fun SupportSQLiteDatabase.insertInput(entryId: Long, position: Int, derivationIndex: Int?, key: ByteArray) {
        insert(
            "coinage_entry_input",
            ContentValues().apply {
                put("entryId", entryId)
                put("position", position)
                put("assetKind", "COIN")
                put("derivationIndex", derivationIndex)
                put("onChainKey", key)
            }
        )
    }

    private fun SupportSQLiteDatabase.blob(sql: String): ByteArray {
        val cursor = query(sql)
        cursor.moveToFirst()
        val value = cursor.getBlob(0)
        cursor.close()

        return value
    }

    private fun migrate(): SupportSQLiteDatabase {
        db.close()
        migratedDb = helper.runMigrationsAndValidate(TEST_DB, 62, true, Migration61To62())

        return migratedDb
    }

    private companion object {
        const val TEST_DB = "migration-61-to-62-test"

        val LEGACY_ZERO = ByteArray(32)
    }
}
