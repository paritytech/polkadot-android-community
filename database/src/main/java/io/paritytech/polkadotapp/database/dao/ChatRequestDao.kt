package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.paritytech.polkadotapp.database.model.ChatRequestLocal
import io.paritytech.polkadotapp.database.model.ChatRequestLocal.DeliveredVia
import io.paritytech.polkadotapp.database.model.ChatRequestLocal.DeliveryStatus
import io.paritytech.polkadotapp.database.model.ChatRequestLocal.Status
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: ChatRequestLocal)

    @Query("SELECT * FROM chat_requests WHERE id = :id")
    suspend fun getById(id: String): ChatRequestLocal?

    @Query("SELECT * FROM chat_requests WHERE id = :id")
    fun subscribeById(id: String): Flow<ChatRequestLocal?>

    @Query("SELECT * FROM chat_requests")
    suspend fun getAll(): List<ChatRequestLocal>

    @Query("SELECT * FROM chat_requests WHERE statusChangedAt > :after")
    suspend fun getStatusChangedAfter(after: Long): List<ChatRequestLocal>

    @Query("SELECT MAX(statusChangedAt) FROM chat_requests")
    fun observeLastStatusChangedAt(): Flow<Long?>

    @Query("UPDATE chat_requests SET status = :status, statusChangedAt = :changedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: Status, changedAt: Long)

    @Query("UPDATE chat_requests SET statusChangedAt = :changedAt WHERE id = :id")
    suspend fun updateStatusChangedAt(id: String, changedAt: Long)

    @Query(
        """
        UPDATE chat_requests
        SET deliveryStatus = :deliveryStatus, deliveredVia = :deliveredVia, lastDeliveredPeriod = :lastDeliveredPeriod
        WHERE id = :id
        """
    )
    suspend fun updateDelivery(
        id: String,
        deliveryStatus: DeliveryStatus,
        deliveredVia: DeliveredVia?,
        lastDeliveredPeriod: Long?,
    )

    @Query("DELETE FROM chat_requests WHERE id = :id")
    suspend fun delete(id: String)
}
