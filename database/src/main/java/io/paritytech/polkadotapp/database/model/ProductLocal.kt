package io.paritytech.polkadotapp.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
class ProductLocal(
    @PrimaryKey val id: String,
    val name: String,
    val scriptUrl: String,
    val contentHash: String?,
)
