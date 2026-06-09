package io.paritytech.polkadotapp.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "coinage_transfer_wal",
    indices = [Index(value = ["chainId"])]
)
class CoinageTransferWalLocal(
    @PrimaryKey val id: String,
    val chainId: String,
    val inputCoinIndices: List<Int>,
    val inputVoucherIndices: List<Int>,
    val expectedOutputCoinIndices: List<Int>,
    val checkpointBlockNumber: Long,
    val checkpointBlockHash: String,
    val mortalityBlocks: Long,
    val createdAt: Long,
)
