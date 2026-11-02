package io.paritytech.polkadotapp.database.migrations

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration70To71Spec : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE chat_requests SET peerAccountId = COALESCE(
                (SELECT accountId FROM contacts WHERE contacts.chatRequestId = chat_requests.id),
                (SELECT contactAccountId FROM chat_acceptances WHERE chat_acceptances.requestId = chat_requests.id),
                (SELECT chatId FROM chat_messages WHERE chat_messages.id = chat_requests.id)
            )
            """.trimIndent()
        )
    }
}
