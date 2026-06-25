package io.paritytech.polkadotapp.feature_products_impl.domain.spaBrowser

import io.paritytech.polkadotapp.feature_chats_api.domain.ReadOnlyChatRoomRepository
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.toChatExtensionId
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductIntegrationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.storage.kiosk.KioskPinStorage
import io.paritytech.polkadotapp.feature_products_impl.domain.product.IntegrationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

interface SpaBrowserInteractor {
    suspend fun installChatAndAwaitRoomCreated(productId: ProductId): Result<ChatId>

    fun saveKioskPin(pin: String): Result<Unit>

    fun verifyKioskPin(input: String): Boolean

    fun clearKioskPin()
}

class RealSpaBrowserInteractor @Inject constructor(
    private val integrationRepository: ProductIntegrationRepository,
    private val chatRoomRepository: ReadOnlyChatRoomRepository,
    private val kioskPinStorage: KioskPinStorage,
) : SpaBrowserInteractor {
    override suspend fun installChatAndAwaitRoomCreated(productId: ProductId): Result<ChatId> = runCatching {
        integrationRepository.install(productId, IntegrationType.Chat)

        val extensionId = productId.toChatExtensionId()

        chatRoomRepository.subscribeRoomsByExtension(extensionId)
            .mapNotNull { rooms -> rooms.firstOrNull() }
            .first()
    }

    override fun saveKioskPin(pin: String): Result<Unit> = runCatching { kioskPinStorage.savePin(pin) }

    override fun verifyKioskPin(input: String): Boolean = kioskPinStorage.readPin() == input

    override fun clearKioskPin() = kioskPinStorage.clearPin()
}
