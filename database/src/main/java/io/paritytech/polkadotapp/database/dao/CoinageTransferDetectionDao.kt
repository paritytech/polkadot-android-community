package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.paritytech.polkadotapp.database.model.CoinageTransferDetectionLocal

@Dao
interface CoinageTransferDetectionDao {
    @Query("SELECT * FROM chat_coinage_transfer_detection WHERE messageId = :messageId")
    suspend fun getCoinageTransferDetection(messageId: String): CoinageTransferDetectionLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoinageTransferDetection(detection: CoinageTransferDetectionLocal)
}
