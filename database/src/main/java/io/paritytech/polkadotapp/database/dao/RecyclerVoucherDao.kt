package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.paritytech.polkadotapp.database.model.RecyclerVoucherLocal
import kotlinx.coroutines.flow.Flow

private const val VOUCHERS_IN_RECYCLER_QUERY = "SELECT * FROM recycler_vouchers WHERE locationRecyclerIndex IS NOT NULL"
private const val ALL_VOUCHERS_QUERY = "SELECT * FROM recycler_vouchers"

@Dao
interface RecyclerVoucherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(voucher: RecyclerVoucherLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vouchers: List<RecyclerVoucherLocal>)

    // A freshly allocated key must never land on an existing row: that row's key may already be handed off.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNew(vouchers: List<RecyclerVoucherLocal>)

    @Query("SELECT * FROM recycler_vouchers")
    fun subscribeAll(): Flow<List<RecyclerVoucherLocal>>

    @Query(
        """
        UPDATE recycler_vouchers
        SET locationRecyclerIndex = :recyclerIndex,
            recyclerMembers = :recyclerMembers,
            enteredAt = CASE WHEN locationRecyclerIndex = :recyclerIndex
                THEN COALESCE(enteredAt, :enteredAt) ELSE :enteredAt END
        WHERE ringVrfPublicKey = :ringVrfPublicKey
        """
    )
    suspend fun updateLocation(
        ringVrfPublicKey: ByteArray,
        recyclerIndex: Int,
        recyclerMembers: Int,
        enteredAt: Long?
    )

    @Transaction
    suspend fun updateLocations(updates: List<RecyclerVoucherLocationUpdate>) {
        updates.forEach { update ->
            updateLocation(update.ringVrfPublicKey, update.recyclerIndex, update.recyclerMembers, update.enteredAt)
        }
    }

    @Query("SELECT * FROM recycler_vouchers WHERE installationId = :installationId AND ringVrfKeyIndex IN (:indices)")
    suspend fun getByRingVrfKeyIndices(installationId: ByteArray, indices: List<Int>): List<RecyclerVoucherLocal>

    @Query("SELECT MAX(ringVrfKeyIndex) FROM recycler_vouchers WHERE installationId = :installationId")
    suspend fun getMaxRingVrfKeyIndex(installationId: ByteArray): Int?

    @Query(VOUCHERS_IN_RECYCLER_QUERY)
    suspend fun getVouchersInRecycler(): List<RecyclerVoucherLocal>

    @Query(VOUCHERS_IN_RECYCLER_QUERY)
    fun subscribeVouchersInRecycler(): Flow<List<RecyclerVoucherLocal>>

    @Query(ALL_VOUCHERS_QUERY)
    suspend fun getAllVouchers(): List<RecyclerVoucherLocal>

    @Query(ALL_VOUCHERS_QUERY)
    fun subscribeAllVouchers(): Flow<List<RecyclerVoucherLocal>>
}

class RecyclerVoucherLocationUpdate(
    val ringVrfPublicKey: ByteArray,
    val recyclerIndex: Int,
    val recyclerMembers: Int,
    val enteredAt: Long?
)
