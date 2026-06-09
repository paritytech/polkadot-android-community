package io.paritytech.polkadotapp.feature_products_impl.data.repository

import io.paritytech.polkadotapp.database.dao.ProductDao
import io.paritytech.polkadotapp.database.model.ProductLocal
import io.paritytech.polkadotapp.feature_products_api.model.Product
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>

    suspend fun getProductById(id: ProductId): Product?

    suspend fun addProduct(id: ProductId, name: String, scriptUrl: String): ProductId

    suspend fun updateProduct(id: ProductId, name: String, scriptUrl: String)

    suspend fun updateContentHash(id: ProductId, contentHash: String)

    suspend fun deleteProduct(id: ProductId)
}

@Singleton
class RealProductRepository @Inject constructor(
    private val productDao: ProductDao,
) : ProductRepository {
    override fun observeProducts(): Flow<List<Product>> {
        return productDao.observeAll()
            .map { products -> products.map { it.toProduct() } }
    }

    override suspend fun getProductById(id: ProductId): Product? {
        return productDao.getById(id.value)?.toProduct()
    }

    override suspend fun addProduct(id: ProductId, name: String, scriptUrl: String): ProductId {
        productDao.insert(
            ProductLocal(
                id = id.value,
                name = name,
                scriptUrl = scriptUrl,
                contentHash = null
            )
        )
        return id
    }

    override suspend fun updateProduct(id: ProductId, name: String, scriptUrl: String) {
        productDao.update(id = id.value, name = name, scriptUrl = scriptUrl)
    }

    override suspend fun updateContentHash(id: ProductId, contentHash: String) {
        productDao.updateContentHash(id = id.value, contentHash = contentHash)
    }

    override suspend fun deleteProduct(id: ProductId) {
        productDao.deleteById(id.value)
    }
}
