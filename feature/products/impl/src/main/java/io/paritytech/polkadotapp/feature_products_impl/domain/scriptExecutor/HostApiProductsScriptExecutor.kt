package io.paritytech.polkadotapp.feature_products_impl.domain.scriptExecutor

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.chains.util.scaleEncodeBinary
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.HexString
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_products_api.model.JsUiEvent
import io.paritytech.polkadotapp.feature_products_api.model.JsWidget
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApi
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.ExplicitInjection
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiEnvironment
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostApiSession
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.HostCallGroupFactory
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.ChatRenderWidgetHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.HostCallHandlerGroup
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import io.paritytech.polkadotapp.feature_products_impl.domain.jsRuntime.WebViewRuntime
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductScriptResolver
import io.paritytech.polkadotapp.feature_products_impl.domain.serialization.JsWidgetSerializer
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.ChatWebViewProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class HostApiProductsScriptExecutor @AssistedInject constructor(
    private val serializer: JsWidgetSerializer,
    private val scriptResolver: ProductScriptResolver,
    private val hostCallGroupFactory: HostCallGroupFactory,
    private val sessionFactory: HostApiSession.Factory,
    private val chatWebViewProviderFactory: ChatWebViewProvider.Factory,
    @Assisted private val productId: ProductId,
) : ProductsScriptExecutor {
    @AssistedFactory
    interface Factory {
        fun create(productId: ProductId): HostApiProductsScriptExecutor
    }

    private val mutex = Mutex()

    private val chatRenderWidgetHostCalls = ChatRenderWidgetHostCalls()

    private var session: HostApiSession? = null
    private var scope: CoroutineScope? = null
    private var initialized = false

    override suspend fun initializeBot(botApi: ProductsBotApi, scope: CoroutineScope): Result<Unit> = runCatching {
        this.scope = scope
        mutex.withLock {
            if (initialized) return@withLock

            val webViewProvider = chatWebViewProviderFactory.create(productId, scope)
            val webViewRuntime = WebViewRuntime(webViewProvider)

            val transport = webViewRuntime.createTransport()
            val navigationPolicy = NavigationPolicy.Disabled

            val sharedGroups = hostCallGroupFactory.createShared(botApi, webViewProvider.callingProductIdProvider, navigationPolicy)
            val chatGroup = hostCallGroupFactory.createChatGroup(botApi)
            val allGroups: List<HostCallHandlerGroup> = sharedGroups + chatGroup + chatRenderWidgetHostCalls

            val environment = HostApiEnvironment(
                navigationPolicy = navigationPolicy,
                injectionStrategy = ExplicitInjection(),
                handlerGroups = allGroups,
            )

            val hostApiSession = sessionFactory.create(environment, webViewRuntime, transport, scope)
            hostApiSession.initialize()

            val workerScript = scriptResolver.resolveScript(productId).getOrThrow()
            hostApiSession.evaluateModuleScript(workerScript)
                .logFailure("Failed to load script for product: $productId")

            this.session = hostApiSession
            initialized = true

            Timber.d("Initialized HostApi script executor for product: $productId")
        }
    }

    override suspend fun onUserMessage(text: String): Result<Unit> = runCatching {
        requireInitialized()
        val escapedText = text.escapeForJs()
        session!!.evaluateScript("dispatchUserMessage('', '$escapedText')")
            .onFailure { Timber.e(it, "Failed to call onUserMessage for product: $productId") }
    }

    override fun renderMessage(
        messageId: ChatMessageId,
        messageType: String,
        messageData: DataByteArray,
    ): Flow<Result<JsWidget>> {
        return flow {
            startRendering(messageId, messageType, messageData)
                .onFailure { emit(Result.failure(it)); return@flow }

            val renderingUpdates = chatRenderWidgetHostCalls.renderUpdatesForMessage(messageId)
                .map { update -> serializer.deserialize(update) }
            emitAll(renderingUpdates)
        }.onCompletion {
            chatRenderWidgetHostCalls.removeMessage(messageId)
        }
    }

    override fun dispatchEvent(event: JsUiEvent) {
        scope?.launch {
            try {
                requireInitialized()
                val escapedMessageId = event.messageId.escapeForJs()
                val escapedActionId = event.actionId.escapeForJs()
                val escapedPayload = encodePayload(event).escapeForJs()
                session!!.evaluateScript("dispatchChatAction('', '$escapedMessageId', '$escapedActionId', '$escapedPayload')")
            } catch (e: Exception) {
                Timber.e(e, "Failed to dispatch event: ${event.actionId}")
            }
        }
    }

    private fun requireInitialized() {
        check(initialized) { "Script executor not initialized for product: $productId" }
    }

    private suspend fun startRendering(
        messageId: ChatMessageId,
        messageType: String,
        messageData: DataByteArray,
    ): Result<Unit> = runCatching {
        requireInitialized()
        val js = buildInitiateRenderingJs(messageType, messageData, messageId)
        session!!.evaluateScript(js)
    }

    private fun buildInitiateRenderingJs(
        messageType: String,
        data: DataByteArray,
        messageId: ChatMessageId,
    ): String {
        val hexData = data.value.toHexString(withPrefix = true)
        val escapedMessageId = messageId.replace("\"", "\\\"")
        val escapedMessageType = messageType.replace("\"", "\\\"")

        return """
            (function() {
                try {
                    if (typeof window.renderMessage === 'function') {
                        window.renderMessage("$escapedMessageType", "$hexData", "$escapedMessageId");
                    }
                } catch (e) {
                    console.error('renderMessage error:', e);
                }
            })();
        """.trimIndent()
    }

    private fun encodePayload(event: JsUiEvent): HexString? {
        return when (val type = event.eventType) {
            JsUiEvent.Type.ButtonClick -> null
            is JsUiEvent.Type.InputFieldValueChange -> type.newValue.scaleEncodeBinary()
                .toHexString(withPrefix = true)
        }
    }

    private fun String?.escapeForJs(): String {
        if (this == null) return "undefined"
        return this
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
