package io.paritytech.polkadotapp.feature_products_impl.domain.productBotManagement

import io.paritytech.polkadotapp.feature_chats_api.domain.middleware.bot.ChatBotStateController
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsContentSeeder
import io.paritytech.polkadotapp.feature_products_api.model.Product
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.toChatExtensionId
import io.paritytech.polkadotapp.feature_products_impl.data.network.ProductScriptDownloader
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductIntegrationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductRepository
import io.paritytech.polkadotapp.feature_products_impl.domain.product.IntegrationType
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductRegistrar
import io.paritytech.polkadotapp.feature_products_impl.domain.product.UninstallProductUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface ProductBotManagementInteractor {
    fun observeProducts(): Flow<List<Product>>

    suspend fun getProduct(productId: ProductId): Product?

    suspend fun upsertProduct(productId: ProductId, scriptUrl: String, name: String): Result<ProductId>

    suspend fun updateProduct(productId: ProductId, scriptUrl: String, name: String): Result<Unit>

    suspend fun deleteProduct(productId: ProductId): Result<Unit>

    suspend fun installChatIntegration(productId: ProductId): Result<Unit>
}

/**
 * Debug menu interactor that acts as a content pre-seeder.
 *
 * Downloads script from HTTP, writes it into DotNs content storage
 * so the main system (ArchiveScriptResolver) can find it at `worker/index.js`.
 * The main system never knows the product wasn't resolved from chain.
 */
@Singleton
class RealProductBotManagementInteractor @Inject constructor(
    private val scriptDownloader: ProductScriptDownloader,
    private val contentSeeder: DotNsContentSeeder,
    private val productRegistrar: ProductRegistrar,
    private val productRepository: ProductRepository,
    private val integrationRepository: ProductIntegrationRepository,
    private val botStateController: ChatBotStateController,
    private val uninstallProductUseCase: UninstallProductUseCase,
) : ProductBotManagementInteractor {
    override fun observeProducts(): Flow<List<Product>> {
        return productRepository.observeProducts()
    }

    override suspend fun getProduct(productId: ProductId): Product? {
        return productRepository.getProductById(productId)
    }

    override suspend fun upsertProduct(productId: ProductId, scriptUrl: String, name: String): Result<ProductId> {
        return scriptDownloader.download(scriptUrl)
            .mapCatching { scriptContent ->
                val contentHash = contentSeeder.seedContent(
                    dotNsName = productId.value,
                    files = mapOf(WORKER_SCRIPT_PATH to scriptContent.toByteArray())
                )

                productRegistrar.ensureRegistered(productId, contentHash)
                productRepository.updateProduct(productId, name, scriptUrl)
                integrationRepository.install(productId, IntegrationType.Chat)
                botStateController.setActive(productId.toChatExtensionId())
                productId
            }
    }

    override suspend fun updateProduct(productId: ProductId, scriptUrl: String, name: String): Result<Unit> {
        return scriptDownloader.download(scriptUrl)
            .mapCatching { scriptContent ->
                contentSeeder.seedContent(
                    dotNsName = productId.value,
                    files = mapOf(WORKER_SCRIPT_PATH to scriptContent.toByteArray())
                )
                productRepository.updateProduct(productId, name, scriptUrl)
            }
    }

    override suspend fun deleteProduct(productId: ProductId): Result<Unit> {
        return uninstallProductUseCase(productId)
    }

    override suspend fun installChatIntegration(productId: ProductId): Result<Unit> {
        return runCatching {
            integrationRepository.install(productId, IntegrationType.Chat)
        }
    }

    companion object {
        private const val WORKER_SCRIPT_PATH = "worker/index.js"
    }
}
