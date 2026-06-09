package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.paritytech.polkadotapp.database.model.RecyclerVoucherLocal
import kotlinx.coroutines.flow.Flow

private const val VOUCHERS_IN_RECYCLER_QUERY = "SELECT * FROM RECYCLER_VOUCHERS WHERE locationRecyclerIndex IS NOT NULL AND usageState IS :usageState"
private const val VOUCHERS_BY_STATE_QUERY = "SELECT * FROM recycler_vouchers WHERE usageState IS :usageState"

@Dao
interface RecyclerVoucherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(voucher: RecyclerVoucherLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vouchers: List<RecyclerVoucherLocal>)

    @Query("SELECT * FROM recycler_vouchers")
    fun subscribeAll(): Flow<List<RecyclerVoucherLocal>>

    @Query(
        """
        SELECT * FROM recycler_vouchers
        WHERE locationRecyclerIndex IS NULL
        """
    )
    fun subscribeNotInRecycler(): Flow<List<RecyclerVoucherLocal>>

    @Query(
        """
        UPDATE recycler_vouchers
        SET locationRecyclerIndex = :recyclerIndex
        WHERE ringVrfPublicKey = :ringVrfPublicKey
        """
    )
    suspend fun updateLocation(
        ringVrfPublicKey: ByteArray,
        recyclerIndex: Int
    )

    @Transaction
    suspend fun updateLocations(updates: List<RecyclerVoucherLocationUpdate>) {
        updates.forEach { update ->
            updateLocation(update.ringVrfPublicKey, update.recyclerIndex)
        }
    }

    @Query(
        """
        UPDATE recycler_vouchers
        SET ringHasEnoughRingMembersToWithdraw = :hasEnough
        WHERE ringVrfKeyIndex = :ringVrfKeyIndex
        """
    )
    suspend fun updateRingMemberStatus(ringVrfKeyIndex: Int, hasEnough: Boolean)

    @Transaction
    suspend fun updateRingMemberStatuses(updates: List<RingMemberStatusUpdate>) {
        updates.forEach { update ->
            updateRingMemberStatus(update.ringVrfKeyIndex, update.hasEnough)
        }
    }

    @Query("DELETE FROM recycler_vouchers WHERE ringVrfKeyIndex = :ringVrfKeyIndex")
    suspend fun removeVoucher(ringVrfKeyIndex: Int)

    @Query("UPDATE recycler_vouchers SET usageState = :usageState WHERE ringVrfKeyIndex = :ringVrfKeyIndex")
    suspend fun setUsageStateByRingVrfKeyIndex(ringVrfKeyIndex: Int, usageState: RecyclerVoucherLocal.UsageState)

    @Query("UPDATE recycler_vouchers SET usageState = :usageState WHERE ringVrfKeyIndex IN (:indices)")
    suspend fun setUsageStateByRingVrfKeyIndices(indices: List<Int>, usageState: RecyclerVoucherLocal.UsageState)

    @Query("SELECT * FROM recycler_vouchers WHERE ringVrfKeyIndex IN (:indices)")
    suspend fun getByRingVrfKeyIndices(indices: List<Int>): List<RecyclerVoucherLocal>

    @Transaction
    suspend fun removeVouchers(ringVrfKeyIndices: List<Int>) {
        ringVrfKeyIndices.forEach { removeVoucher(it) }
    }

    @Query("SELECT MAX(ringVrfKeyIndex) FROM recycler_vouchers")
    suspend fun getMaxRingVrfKeyIndex(): Int?

    @Query(VOUCHERS_IN_RECYCLER_QUERY)
    suspend fun getVouchersInRecyclerByUsageState(usageState: RecyclerVoucherLocal.UsageState): List<RecyclerVoucherLocal>

    @Query(VOUCHERS_IN_RECYCLER_QUERY)
    fun subscribeVouchersInRecyclerByUsageState(usageState: RecyclerVoucherLocal.UsageState): Flow<List<RecyclerVoucherLocal>>

    @Query(VOUCHERS_BY_STATE_QUERY)
    suspend fun getAllVouchersByUsageState(usageState: RecyclerVoucherLocal.UsageState): List<RecyclerVoucherLocal>

    @Query(VOUCHERS_BY_STATE_QUERY)
    fun subscribeAllVouchersByUsageState(usageState: RecyclerVoucherLocal.UsageState): Flow<List<RecyclerVoucherLocal>>
}

class RecyclerVoucherLocationUpdate(
    val ringVrfPublicKey: ByteArray,
    val recyclerIndex: Int
)

class RingMemberStatusUpdate(
    val ringVrfKeyIndex: Int,
    val hasEnough: Boolean
)
