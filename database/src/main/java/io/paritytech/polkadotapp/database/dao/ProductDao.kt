package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import io.paritytech.polkadotapp.database.model.ProductLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products")
    fun observeAll(): Flow<List<ProductLocal>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductLocal?

    @Insert(onConflict = REPLACE)
    suspend fun insert(product: ProductLocal)

    @Query("UPDATE products SET name = :name, scriptUrl = :scriptUrl WHERE id = :id")
    suspend fun update(id: String, name: String, scriptUrl: String)

    @Query("UPDATE products SET contentHash = :contentHash WHERE id = :id")
    suspend fun updateContentHash(id: String, contentHash: String)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)
}
