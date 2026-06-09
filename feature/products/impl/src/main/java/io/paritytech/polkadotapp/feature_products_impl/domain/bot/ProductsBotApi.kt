package io.paritytech.polkadotapp.feature_products_impl.domain.bot

import io.paritytech.polkadotapp.bandersnatch_crypto.ContextualAlias
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.GenesisHash
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.PaymentId
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.PaymentStatus
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.AllocatableResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.AllocationOutcome
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.signing.SignedTransaction
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.CreateProductRoomRequest
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.CreateProductRoomResult
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.ProductChatIdParameter
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.model.ProductChatRoom
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.GetUserIdResult
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.LegacyAccountResult
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.PaymentBalance
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.ProductAccountResult
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.ProductTheme
import io.paritytech.polkadotapp.feature_products_impl.domain.notifications.NotificationId
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.DeviceCapabilityType
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.RemotePermissionRequest
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.PaymentTopUpSource
import io.paritytech.polkadotapp.feature_statement_store_api.data.Statement
import io.paritytech.polkadotapp.feature_statement_store_api.data.StatementsPage
import io.paritytech.polkadotapp.feature_statement_store_api.data.TopicFilter
import io.paritytech.polkadotapp.feature_statement_store_api.data.models.StatementStoreMessageProof
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed interface ProductBotMessage {
    data class Custom(val messageType: String, val data: DataByteArray) : ProductBotMessage

    data class Text(val text: String) : ProductBotMessage
}

interface ProductsBotApi {
    suspend fun createRoom(request: CreateProductRoomRequest): Result<CreateProductRoomResult>

    suspend fun sendMessage(chatIdParameter: ProductChatIdParameter, message: ProductBotMessage): Result<ChatMessageId>

    suspend fun accountGet(callingProductId: ProductId, productAccountId: ProductAccountId): Result<ProductAccountResult>

    suspend fun accountGetAlias(callingProductId: ProductId, productAccountId: ProductAccountId): Result<ContextualAlias>

    suspend fun getUserId(callingProductId: ProductId): Result<GetUserIdResult>

    suspend fun chainNodes(genesisHash: GenesisHash): Result<List<String>>

    suspend fun chainSupported(genesisHash: GenesisHash): Result<Boolean>

    suspend fun signPayload(signingRequestBody: SigningRequestBody.Transaction): Result<SignedTransaction.PayloadJson>

    suspend fun signRaw(signingRequestBody: SigningRequestBody.Raw): Result<SignedTransaction.Raw>

    suspend fun signCreateTransaction(signingRequestBody: SigningRequestBody.CreateTransaction): Result<SignedTransaction.GeneralTransaction>

    suspend fun getLegacyAccounts(): Result<List<LegacyAccountResult>>

    suspend fun lookupPreimage(hash: ByteArray): Result<ByteArray>

    suspend fun submitPreimage(callingProductId: ProductId, data: ByteArray): Result<String>

    suspend fun createStatementProof(statementBody: Statement.Body): Result<StatementStoreMessageProof>

    suspend fun createStatementProofAuthorized(
        callingProductId: ProductId,
        statementBody: Statement.Body,
    ): Result<StatementStoreMessageProof>

    suspend fun requestResourceAllocation(
        callingProductId: ProductId,
        resources: List<AllocatableResource>,
    ): Result<List<AllocationOutcome>>

    suspend fun requestDevicePermission(callingProductId: ProductId, capability: DeviceCapabilityType): Result<Boolean>

    suspend fun requestRemotePermissions(
        callingProductId: ProductId,
        requests: List<RemotePermissionRequest>,
    ): Result<Boolean>

    @OptIn(ExperimentalTime::class)
    suspend fun publishNotification(
        callingProductId: ProductId,
        text: String,
        deeplink: String?,
        scheduledAt: Instant?,
    ): Result<NotificationId>

    suspend fun cancelNotification(callingProductId: ProductId, notificationId: NotificationId): Result<Unit>

    suspend fun submitStatement(callingProductId: ProductId, statement: Statement): Result<Unit>

    fun subscribeStatements(filter: TopicFilter): Flow<StatementsPage>

    suspend fun deriveEntropy(callingProductId: ProductId, key: ByteArray): Result<ByteArray>

    fun subscribePaymentBalance(callingProductId: ProductId): Flow<PaymentBalance>

    suspend fun requestPayment(
        callingProductId: ProductId,
        amount: Balance,
        destination: AccountId,
    ): Result<PaymentId>

    suspend fun topUp(
        callingProductId: ProductId,
        amount: Balance,
        source: PaymentTopUpSource,
    ): Result<Unit>

    fun subscribePaymentStatus(
        callingProductId: ProductId,
        paymentId: PaymentId,
    ): Flow<PaymentStatus>

    fun subscribeChatRooms(): Flow<List<ProductChatRoom>>

    fun subscribeTheme(): Flow<ProductTheme>
}
