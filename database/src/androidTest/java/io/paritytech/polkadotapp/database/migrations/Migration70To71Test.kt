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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration70To71Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private lateinit var migratedDb: SupportSQLiteDatabase

    @After
    fun tearDown() {
        if (::migratedDb.isInitialized) migratedDb.close()
    }

    @Test
    fun requestPeerIsTakenFromItsContactAcceptanceOrWelcomeMessage() {
        val pendingPeer = ByteArray(32) { 1 }
        val acceptedPeer = ByteArray(32) { 2 }
        val welcomedPeer = ByteArray(32) { 3 }

        helper.createDatabase(TEST_DB, 70).apply {
            insertRequest("pending", status = "PENDING")
            insertRequest("accepted", status = "ACCEPTED")
            insertRequest("welcomed", status = "ACCEPTED")
            insertRequest("unknown", status = "ACCEPTED")
            insertContact(pendingPeer, chatRequestId = "pending")
            insertContact(acceptedPeer, chatRequestId = null)
            insert(
                "chat_acceptances",
                ContentValues().apply {
                    put("contactAccountId", acceptedPeer)
                    put("requestId", "accepted")
                    put("messageId", "accepted-message")
                    put("acceptedAt", 1L)
                    put("deliveryStatus", "UNDELIVERED")
                }
            )
            insert(
                "chat_messages",
                ContentValues().apply {
                    put("id", "welcomed")
                    put("chatId", welcomedPeer)
                    put("timestamp", 1L)
                    put("status", "NEW")
                    put("type", "CHAT_REQUEST")
                    put("searchableContent", "")
                    put("content", byteArrayOf())
                    put("isInternal", 0)
                    put("origintype", "CONTACT")
                }
            )
            close()
        }

        migratedDb = helper.runMigrationsAndValidate(TEST_DB, 71, true)

        assertArrayEquals(pendingPeer, migratedDb.peerOf("pending"))
        assertArrayEquals(acceptedPeer, migratedDb.peerOf("accepted"))
        assertArrayEquals(welcomedPeer, migratedDb.peerOf("welcomed"))
        assertNull(migratedDb.peerOf("unknown"))
    }

    private fun SupportSQLiteDatabase.insertRequest(id: String, status: String) {
        insert(
            "chat_requests",
            ContentValues().apply {
                put("id", id)
                put("timestamp", 1L)
                put("direction", "INCOMING")
                put("status", status)
            }
        )
    }

    private fun SupportSQLiteDatabase.insertContact(accountId: ByteArray, chatRequestId: String?) {
        insert(
            "contacts",
            ContentValues().apply {
                put("accountId", accountId)
                put("chatKey", ByteArray(32))
                put("ourMetaAccountId", 1L)
                put("sharedSecretDerivationPath", "chat")
                put("chatRequestId", chatRequestId)
            }
        )
    }

    private fun SupportSQLiteDatabase.insert(table: String, values: ContentValues) {
        insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
    }

    private fun SupportSQLiteDatabase.peerOf(requestId: String): ByteArray? {
        val cursor = query("SELECT peerAccountId FROM chat_requests WHERE id = ?", arrayOf(requestId))
        cursor.moveToFirst()
        val peer = cursor.getBlob(0)
        cursor.close()
        return peer
    }

    private companion object {
        const val TEST_DB = "migration-70-to-71-test"
    }
}
