package io.paritytech.polkadotapp.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The coinage ledger becomes the durability engine's ledger, shared by every domain.
 *
 * Ids are carried over unchanged: `coinage_entry_input`, `coinage_entry_output` and the asset-state query
 * all reference `entryId`, and those rows stay exactly where they are. Only the row describing the
 * transaction itself moves, gaining the domain that owns it.
 */
class Migration60To61 : Migration(60, 61) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_DURABLE_TX)
        db.execSQL(INDEX_DOMAIN_GROUP)
        db.execSQL(INDEX_DOMAIN_STATUS)

        db.execSQL(
            """
            INSERT INTO `durable_tx` (
                `id`, `domainId`, `operationGroupId`, `txHash`, `mortalityBlocks`, `status`,
                `checkpointblockNumber`, `checkpointblockHash`,
                `successDetectedblockNumber`, `successDetectedblockHash`
            )
            SELECT
                `id`, '$COINAGE_DOMAIN_ID', `operationGroupId`, `txHash`, `mortalityBlocks`, `status`,
                `checkpointblockNumber`, `checkpointblockHash`,
                `successDetectedblockNumber`, `successDetectedblockHash`
            FROM `coinage_entry`
            """.trimIndent()
        )

        // AUTOINCREMENT reads its next value from sqlite_sequence, so without this a fresh insert would
        // reuse an id the copied rows already hold — and a domain's asset rows are keyed on exactly that id.
        db.execSQL(
            """
            INSERT OR REPLACE INTO `sqlite_sequence` (`name`, `seq`)
            SELECT 'durable_tx', COALESCE(MAX(`id`), 0) FROM `durable_tx`
            """.trimIndent()
        )

        db.execSQL("DROP TABLE `coinage_entry`")
    }

    private companion object {
        /** Must stay byte-identical to what Room generates for `DurableTxLocal`; see `schemas/61.json`. */
        const val CREATE_DURABLE_TX =
            "CREATE TABLE IF NOT EXISTS `durable_tx` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`domainId` TEXT NOT NULL, " +
                "`operationGroupId` TEXT, " +
                "`txHash` TEXT NOT NULL, " +
                "`mortalityBlocks` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`checkpointblockNumber` INTEGER NOT NULL, " +
                "`checkpointblockHash` TEXT NOT NULL, " +
                "`successDetectedblockNumber` INTEGER, " +
                "`successDetectedblockHash` TEXT)"

        const val INDEX_DOMAIN_GROUP =
            "CREATE INDEX IF NOT EXISTS `index_durable_tx_domainId_operationGroupId` " +
                "ON `durable_tx` (`domainId`, `operationGroupId`)"

        const val INDEX_DOMAIN_STATUS =
            "CREATE INDEX IF NOT EXISTS `index_durable_tx_domainId_status` " +
                "ON `durable_tx` (`domainId`, `status`)"

        const val COINAGE_DOMAIN_ID = "coinage"
    }
}
