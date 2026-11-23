package io.paritytech.polkadotapp.database.migrations

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration70To71Spec : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE chat_messages SET status = 'IS_DELIVERED' WHERE status = 'IS_READ' AND origintype = 'USER'")
    }
}
