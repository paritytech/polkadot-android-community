package io.paritytech.polkadotapp.feature_products_impl.domain.bot

import io.paritytech.polkadotapp.common.utils.childScope
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.ChatExtensionContext
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.CreateRoomRequest
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.DefaultRoomMetadata
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.ExternalExtension
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.NewMessagesRoomFilter
import io.paritytech.polkadotapp.feature_chats_api.domain.middleware.bot.CustomChatMenuRenderer
import io.paritytech.polkadotapp.feature_chats_api.domain.middleware.bot.CustomChatMessageRenderer
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageOrigin
import io.paritytech.polkadotapp.feature_products_api.model.Product
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContextHolder
import io.paritytech.polkadotapp.feature_products_api.model.toChatExtensionId
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.message.ProductsMessageContent
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.message.ProductsMessageRenderer
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.CreateProductRoomRequest
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.CreateProductRoomResult
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.ProductChatIdParameter
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.ProductChatRoom
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.extractProductChatIdParameter
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.toChatId
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.FixedProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.scriptExecutor.ProductsScriptExecutor
import io.paritytech.polkadotapp.feature_products_impl.presentation.bot.menu.ProductChatMenuRenderer
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Products extension that delegates all behavior to JavaScript scripts.
 * Supports multiple rooms — JS scripts can create rooms via [createRoom].
 *
 * Each instance is created by [ProductBotFactory] for a specific [Product].
 */
class ProductChatExtension(
    val product: Product,
    private val scriptExecutor: ProductsScriptExecutor,
    private val messageRenderer: ProductsMessageRenderer,
    private val hostApiInteractor: HostApiInteractor,
    private val signingContextHolder: SigningContextHolder,
    private val productsRouter: ProductsRouter,
) : ExternalExtension() {
    override val id = product.id.toChatExtensionId()

    override val activationStateExternallyControlled: Boolean = false

    override val defaultRoomMetadata: DefaultRoomMetadata = DefaultRoomMetadata(product.name, icon = null)

    private var botScope: CoroutineScope? = null

    override fun customMessageRenderers(): List<CustomChatMessageRenderer<*>> {
        return listOf(messageRenderer)
    }

    override fun customMenuRenderer(chatId: ChatId): CustomChatMenuRenderer = ProductChatMenuRenderer(product, chatId)

    context(ChatExtensionContext)
    override fun startGlobalWork() {
        botScope = scope.childScope(supervised = true)

        botScope?.launch {
            Timber.d("Starting a bot ${product.name}...")

            val bridge = DelegatingProductsBotApi(this@ChatExtensionContext)

            subscribeNewMessages(NewMessagesRoomFilter.AnyFromExtension(id))
                .filter { it.origin !is ChatMessageOrigin.Extension }
                .onEach { message -> routeMessage(message) }
                .launchIn(scope)

            scriptExecutor.initializeBot(bridge, botScope!!)
                .onFailure { error ->
                    Timber.e(error, "Failed to initialize bot script: ${product.id}")
                    return@launch
                }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProductChatExtension) return false
        return product == other.product
    }

    override fun hashCode(): Int = product.hashCode()

    override suspend fun dispose() {
        botScope?.cancel()
        botScope = null
        Timber.d("Disposed product bot: ${product.name}")
    }

    private suspend fun routeMessage(message: ChatMessage) {
        when (val content = message.content) {
            is ChatMessage.Content.Text -> scriptExecutor.onUserMessage(content.text)
            else -> {}
        }
    }

    private inner class DelegatingProductsBotApi(
        private val extensionContext: ChatExtensionContext
    ) : BaseProductsBotApi(
        hostApiInteractor = hostApiInteractor,
        signingContextHolder = signingContextHolder,
        router = productsRouter,
        callingProductIdProvider = FixedProductId(product.id),
    ) {
        override suspend fun createRoom(request: CreateProductRoomRequest): Result<CreateProductRoomResult> {
            val chatId = request.chatIdParameter.toChatId(id)
            val request = CreateRoomRequest(chatId, request.name, request.icon)
            val response = extensionContext.createRoom(request)
            return Result.success(CreateProductRoomResult(response.status))
        }

        override suspend fun sendMessage(chatIdParameter: ProductChatIdParameter, message: ProductBotMessage): Result<ChatMessageId> {
            val chatId = chatIdParameter.toChatId(id)
            val chatMessage = extensionContext.sendMessage(chatId, message.toChatMessageContent())
            return Result.success(chatMessage.id)
        }

        override fun subscribeChatRooms(): Flow<List<ProductChatRoom>> {
            return extensionContext.subscribeOwnRooms().map { chatIds ->
                chatIds.mapNotNull { chatId ->
                    val param = chatId.extractProductChatIdParameter(id)
                        .logFailure("Unexpected state: subscribeOwnRooms returned corrupted chatId: ${chatId.value}")
                        .getOrNull() ?: return@mapNotNull null

                    ProductChatRoom(roomId = param.value, participatingAs = "RoomHost")
                }
            }
        }

        private fun ProductBotMessage.toChatMessageContent(): ChatMessage.Content = when (this) {
            is ProductBotMessage.Text -> ChatMessage.Content.Text(text)
            is ProductBotMessage.Custom -> {
                val messageContent = ProductsMessageContent(messageType, data)
                ChatMessage.Content.Custom(
                    rendererId = messageRenderer.id,
                    content = Result.success(messageContent)
                )
            }
        }
    }
}
