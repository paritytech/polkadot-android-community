package io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement

import io.paritytech.polkadotapp.common.presentation.navigation.ReturnableRouter
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRouter
import io.paritytech.polkadotapp.feature_products_api.presentation.SpaBrowserPayload

interface ProductsRouter : ReturnableRouter, SigningRouter {
    fun openSpaBrowser(payload: SpaBrowserPayload)

    /** Leave the browser for the main screen, which resumes on its last selected bottom tab. */
    fun leaveBrowser()
    fun openProductChat(productId: ProductId)
    fun openChat(chatId: ChatId)
    suspend fun openPermissionPrompt()
    suspend fun openPaymentRequestPrompt()
    suspend fun openTopUpRequestPrompt()
    suspend fun openResourceAllocationRequestPrompt()
    suspend fun openCrossProductProofPrompt()

    /** Confirmation prompt for an action the TrUAPI Rust core is about to take. */
    suspend fun openTrUAPIConfirmation()
    fun openProductSettings(productId: ProductId)
    fun openProductPermissions(productId: ProductId)
}
