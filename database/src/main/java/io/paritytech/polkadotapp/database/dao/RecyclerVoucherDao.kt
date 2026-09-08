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

    @Query("SELECT * FROM recycler_vouchers")
    fun subscribeAll(): Flow<List<RecyclerVoucherLocal>>

    /**
     * The location always; the fungibilities only when they could be worked out.
     *
     * A null *parameter* leaves the stored fungibility standing, the same way [CoinDao.updateCoinPresence]
     * treats a null age. It matters because a fungibility needs two chain reads the location does not — the
     * ring capacity and the unloaded count — and neither failing may cost the voucher its member count,
     * which is what decides spendability.
     *
     * A null *column* is what "the maximum has never been frozen" looks like, so the maximum is written
     * exactly once: on the voucher's first landing in a ring, the only moment all three parts of its
     * recycler key first exist at once. Everything after that keeps the historical value, zero included —
     * a ring already drained when the voucher arrived really does have a maximum of zero, and the
     * `current > max` state the view absorbs depends on that value never moving.
     */
    @Query(
        """
        UPDATE recycler_vouchers
        SET locationRecyclerIndex = :recyclerIndex,
            recyclerMembers = :recyclerMembers,
            recyclerFungibility = COALESCE(:recyclerFungibility, recyclerFungibility),
            maxRecyclerFungibility = CASE
                WHEN :maxRecyclerFungibility IS NULL THEN maxRecyclerFungibility
                WHEN maxRecyclerFungibility IS NULL THEN :maxRecyclerFungibility
                ELSE maxRecyclerFungibility
            END
        WHERE ringVrfPublicKey = :ringVrfPublicKey
        """
    )
    suspend fun updateLocation(
        ringVrfPublicKey: ByteArray,
        recyclerIndex: Int,
        recyclerMembers: Int,
        recyclerFungibility: Int?,
        maxRecyclerFungibility: Int?
    )

    @Transaction
    suspend fun updateLocations(updates: List<RecyclerVoucherLocationUpdate>) {
        updates.forEach { update ->
            updateLocation(
                ringVrfPublicKey = update.ringVrfPublicKey,
                recyclerIndex = update.recyclerIndex,
                recyclerMembers = update.recyclerMembers,
                recyclerFungibility = update.recyclerFungibility,
                maxRecyclerFungibility = update.maxRecyclerFungibility
            )
        }
    }

    @Query("SELECT * FROM recycler_vouchers WHERE ringVrfKeyIndex IN (:indices)")
    suspend fun getByRingVrfKeyIndices(indices: List<Int>): List<RecyclerVoucherLocal>

    @Query("SELECT MAX(ringVrfKeyIndex) FROM recycler_vouchers")
    suspend fun getMaxRingVrfKeyIndex(): Int?

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
    /** Null leaves the stored value standing — see [RecyclerVoucherDao.updateLocation]. */
    val recyclerFungibility: Int?,
    val maxRecyclerFungibility: Int?
)
