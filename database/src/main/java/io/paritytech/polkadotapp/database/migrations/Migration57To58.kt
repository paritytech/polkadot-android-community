package io.paritytech.polkadotapp.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A voucher's ring stops being a yes/no and becomes a count.
 *
 * The boolean answered one hardcoded question — "are there at least 10 members" — so it could not serve a
 * strategy that picks its own threshold, and it was monotone, so it could not fall when a ring was archived.
 * The count is not backfilled: the chain subscription rewrites it for every unsettled voucher on the next
 * pass, and until then a null reads as "not yet known", which is the honest answer.
 */
class Migration57To58 : Migration(57, 58) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recycler_vouchers ADD COLUMN recyclerMembers INTEGER")
        db.execSQL("ALTER TABLE recycler_vouchers DROP COLUMN ringHasEnoughRingMembersToWithdraw")
    }
}
