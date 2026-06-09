package io.paritytech.polkadotapp.feature_products_impl.domain.bot

import io.paritytech.polkadotapp.feature_products_api.model.Product
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContextHolder
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.message.ProductsMessageRenderer
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.scriptExecutor.HostApiProductsScriptExecutor
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating [ProductChatExtension] instances.
 */
@Singleton
class ProductBotFactory @Inject constructor(
    private val scriptExecutorFactory: HostApiProductsScriptExecutor.Factory,
    private val hostApiInteractor: HostApiInteractor,
    private val signingContextHolder: SigningContextHolder,
    private val productsRouter: ProductsRouter
) {
    fun create(product: Product): ProductChatExtension {
        val scriptExecutor = scriptExecutorFactory.create(product.id)

        return ProductChatExtension(
            product = product,
            scriptExecutor = scriptExecutor,
            messageRenderer = ProductsMessageRenderer(product, scriptExecutor),
            hostApiInteractor = hostApiInteractor,
            signingContextHolder = signingContextHolder,
            productsRouter = productsRouter
        )
    }
}
