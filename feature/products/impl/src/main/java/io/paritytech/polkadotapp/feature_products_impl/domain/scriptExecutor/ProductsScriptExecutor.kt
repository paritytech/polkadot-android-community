package io.paritytech.polkadotapp.feature_products_impl.domain.scriptExecutor

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_products_api.model.JsWidget
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Interface for executing Products scripts.
 *
 * The native side provides APIs via [ProductsBotApi] for scripts to send messages.
 */
interface ProductsScriptExecutor : JsEventDispatcher {
    /**
     * Initialize the bot script and set up the bridge for communication.
     * Must be called before any other methods.
     *
     * @param botApi Api for scripts to communicate back to native (send messages, etc.)
     * @param scope Coroutine scope that ties the executor's lifecycle. When cancelled, resources are disposed.
     */
    suspend fun initializeBot(botApi: ProductsBotApi, scope: CoroutineScope): Result<Unit>

    /**
     * Called when the user sends a text message.
     * The script should handle it and may send response messages via the bridge.
     *
     * @param text The user's message text
     */
    suspend fun onUserMessage(text: String): Result<Unit>

    /**
     * Render a widget from script data.
     * Used when displaying a previously sent custom message.
     *
     * @return Flow of rendering updates. [Result.failure] might be returned in case either initialization
     * of the rendering failed or we failed to parse render update
     */
    fun renderMessage(
        messageId: ChatMessageId,
        messageType: String,
        messageData: DataByteArray,
    ): Flow<Result<JsWidget>>
}
