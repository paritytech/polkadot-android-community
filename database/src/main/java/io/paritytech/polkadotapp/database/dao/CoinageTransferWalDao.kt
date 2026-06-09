package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.paritytech.polkadotapp.database.model.CoinageTransferWalLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface CoinageTransferWalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: CoinageTransferWalLocal)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<CoinageTransferWalLocal>)

    @Query("DELETE FROM coinage_transfer_wal WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM coinage_transfer_wal WHERE chainId = :chainId")
    suspend fun getAllForChain(chainId: String): List<CoinageTransferWalLocal>

    @Query("SELECT * FROM coinage_transfer_wal WHERE chainId = :chainId")
    fun observeForChain(chainId: String): Flow<List<CoinageTransferWalLocal>>

    @Query("SELECT * FROM coinage_transfer_wal")
    suspend fun getAll(): List<CoinageTransferWalLocal>
}
