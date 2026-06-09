package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.database.model.CoinLocal
import kotlinx.coroutines.flow.Flow

private const val ACTIVE_COINS_QUERY = "SELECT * FROM coins WHERE spentState = :spentState AND ageValue IS NOT NULL"

@Dao
interface CoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: CoinLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(coins: List<CoinLocal>)

    @Query("SELECT * FROM coins")
    fun subscribeAll(): Flow<List<CoinLocal>>

    @Query("SELECT * FROM coins WHERE spentState = :state")
    fun subscribeCoinsWithSpentState(state: CoinLocal.SpentState): Flow<List<CoinLocal>>

    @Query("SELECT * FROM coins WHERE spentState = :state")
    suspend fun getCoinsWithSpentState(state: CoinLocal.SpentState): List<CoinLocal>

    @Query("SELECT * FROM coins WHERE spentState != :state")
    fun subscribeCoinsExcludingSpentOnChain(state: CoinLocal.SpentState): Flow<List<CoinLocal>>

    @Query("SELECT * FROM coins WHERE ageValue IS NULL")
    fun subscribeAllCoinsWithUnknownAge(): Flow<List<CoinLocal>>

    @Query("SELECT MAX(derivationIndex) FROM coins")
    suspend fun getMaxDerivationIndex(): Int?

    @Query(
        """
        UPDATE coins
        SET ageValue = :age, spentState = :spentState
        WHERE accountId = :accountId
        """
    )
    suspend fun updateCoin(accountId: ByteArray, age: Int, spentState: CoinLocal.SpentState)

    @Transaction
    suspend fun updateCoins(updates: List<CoinUpdateLocal>) {
        updates.forEach {
            updateCoin(
                accountId = it.accountId.value,
                age = it.age,
                spentState = it.spentState
            )
        }
    }

    @Query(ACTIVE_COINS_QUERY)
    suspend fun getAllAgedCoinsWithState(spentState: CoinLocal.SpentState): List<CoinLocal>

    @Query(ACTIVE_COINS_QUERY)
    fun subscribeAllAgedCoinsWithState(spentState: CoinLocal.SpentState): Flow<List<CoinLocal>>

    @Query("SELECT * FROM coins WHERE spentState = :spentState AND ageValue IS NOT NULL AND ageValue >= :minAge")
    suspend fun getCoinsWithKnownAgeAtLeast(spentState: CoinLocal.SpentState, minAge: Int): List<CoinLocal>

    @Query("UPDATE coins SET spentState = :spentState WHERE derivationIndex = :derivationIndex")
    suspend fun setSpentStateByDerivationIndex(derivationIndex: Int, spentState: CoinLocal.SpentState)

    @Query("UPDATE coins SET spentState = :spentState WHERE derivationIndex IN (:indices)")
    suspend fun setSpentStateByDerivationIndices(indices: List<Int>, spentState: CoinLocal.SpentState)

    @Query("DELETE FROM coins WHERE derivationIndex = :derivationIndex")
    suspend fun removeCoin(derivationIndex: Int)

    @Query("DELETE FROM coins WHERE derivationIndex IN (:indices)")
    suspend fun removeCoins(indices: List<Int>)
}

class CoinUpdateLocal(
    val accountId: AccountId,
    val age: Int,
    val spentState: CoinLocal.SpentState
)
