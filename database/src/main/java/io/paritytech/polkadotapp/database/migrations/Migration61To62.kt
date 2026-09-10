package io.paritytech.polkadotapp.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Coinage keys become scoped to the installation that allocated them, so every own-asset row gains the
// installation id its key is derived under.
//
// Rows already here were derived under the single `//0` page, which decodes to 32 zero bytes — exactly the id
// they receive, so every key they name stays the key it was. Received coin inputs name no own key and keep a
// null id, matching their null index.
class Migration61To62 : Migration(61, 62) {
    override fun migrate(db: SupportSQLiteDatabase) {
        recreate(
            db = db,
            table = "coins",
            createSql = """
                CREATE TABLE `coins_new` (
                    `installationId` BLOB NOT NULL,
                    `derivationIndex` INTEGER NOT NULL,
                    `accountId` BLOB NOT NULL,
                    `valueExponent` INTEGER NOT NULL,
                    `ageValue` INTEGER,
                    `onChain` INTEGER NOT NULL,
                    PRIMARY KEY(`installationId`, `derivationIndex`)
                )
            """,
            columns = "`derivationIndex`, `accountId`, `valueExponent`, `ageValue`, `onChain`",
            installationValue = LEGACY_ZERO,
        )

        recreate(
            db = db,
            table = "recycler_vouchers",
            createSql = """
                CREATE TABLE `recycler_vouchers_new` (
                    `installationId` BLOB NOT NULL,
                    `ringVrfKeyIndex` INTEGER NOT NULL,
                    `ringVrfPublicKey` BLOB NOT NULL,
                    `recyclerValue` INTEGER NOT NULL,
                    `locationRecyclerIndex` INTEGER,
                    `recyclerMembers` INTEGER,
                    PRIMARY KEY(`installationId`, `ringVrfKeyIndex`)
                )
            """,
            columns = "`ringVrfKeyIndex`, `ringVrfPublicKey`, `recyclerValue`, `locationRecyclerIndex`, `recyclerMembers`",
            installationValue = LEGACY_ZERO,
        )

        recreate(
            db = db,
            table = "coinage_entry_input",
            createSql = """
                CREATE TABLE `coinage_entry_input_new` (
                    `entryId` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    `assetKind` TEXT NOT NULL,
                    `installationId` BLOB,
                    `derivationIndex` INTEGER,
                    `onChainKey` BLOB NOT NULL,
                    PRIMARY KEY(`entryId`, `position`)
                )
            """,
            columns = "`entryId`, `position`, `assetKind`, `derivationIndex`, `onChainKey`",
            installationValue = "CASE WHEN `derivationIndex` IS NULL THEN NULL ELSE $LEGACY_ZERO END",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_coinage_entry_input_onChainKey` ON `coinage_entry_input` (`onChainKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_coinage_entry_input_entryId` ON `coinage_entry_input` (`entryId`)")

        recreate(
            db = db,
            table = "coinage_entry_output",
            createSql = """
                CREATE TABLE `coinage_entry_output_new` (
                    `entryId` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    `assetKind` TEXT NOT NULL,
                    `installationId` BLOB NOT NULL,
                    `derivationIndex` INTEGER NOT NULL,
                    `onChainKey` BLOB NOT NULL,
                    PRIMARY KEY(`entryId`, `position`)
                )
            """,
            columns = "`entryId`, `position`, `assetKind`, `derivationIndex`, `onChainKey`",
            installationValue = LEGACY_ZERO,
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_coinage_entry_output_onChainKey` ON `coinage_entry_output` (`onChainKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_coinage_entry_output_entryId` ON `coinage_entry_output` (`entryId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_coinage_entry_output_assetKind_installationId_derivationIndex` " +
                "ON `coinage_entry_output` (`assetKind`, `installationId`, `derivationIndex`)"
        )

        recreate(
            db = db,
            table = "coinage_handoff",
            createSql = """
                CREATE TABLE `coinage_handoff_new` (
                    `onChainKey` BLOB NOT NULL,
                    `assetKind` TEXT NOT NULL,
                    `installationId` BLOB NOT NULL,
                    `derivationIndex` INTEGER NOT NULL,
                    `committed` INTEGER NOT NULL,
                    PRIMARY KEY(`onChainKey`)
                )
            """,
            columns = "`onChainKey`, `assetKind`, `derivationIndex`, `committed`",
            installationValue = LEGACY_ZERO,
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_coinage_handoff_assetKind_installationId_derivationIndex` " +
                "ON `coinage_handoff` (`assetKind`, `installationId`, `derivationIndex`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `coinage_installations` (
                `installationId` BLOB NOT NULL,
                `isCurrent` INTEGER NOT NULL,
                `coinScanNextIndex` INTEGER NOT NULL,
                `voucherScanNextIndex` INTEGER NOT NULL,
                `initialScanCompleted` INTEGER NOT NULL,
                PRIMARY KEY(`installationId`)
            )
            """.trimIndent()
        )
    }

    private fun recreate(
        db: SupportSQLiteDatabase,
        table: String,
        createSql: String,
        columns: String,
        installationValue: String,
    ) {
        db.execSQL(createSql.trimIndent())
        db.execSQL("INSERT INTO `${table}_new` (`installationId`, $columns) SELECT $installationValue, $columns FROM `$table`")
        db.execSQL("DROP TABLE `$table`")
        db.execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
    }

    private companion object {
        const val LEGACY_ZERO = "zeroblob(32)"
    }
}
