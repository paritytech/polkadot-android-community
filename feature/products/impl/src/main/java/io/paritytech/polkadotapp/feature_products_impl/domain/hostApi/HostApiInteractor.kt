@file:OptIn(ExperimentalTime::class)

package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi

import androidx.compose.ui.graphics.Color
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519SubstrateKeypairFactory
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.bandersnatch_crypto.ContextualAlias
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.GenesisHash
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ConnectionSecrets
import io.paritytech.polkadotapp.chains.multiNetwork.connection.saturateNodeUrls
import io.paritytech.polkadotapp.chains.multiNetwork.getChain
import io.paritytech.polkadotapp.chains.multiNetwork.getChainOrNull
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.util.wssNodes
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.bytes
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flowOfAll
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.design.theme.AppThemeSelector
import io.paritytech.polkadotapp.designsystem.themes.PolkadotAppTheme
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentService
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.PaymentId
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.PaymentStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_products_api.domain.GetContextualAliasUseCase
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.AccountsProtocol
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.AllocatableResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.AllocationOutcome
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocationOutcome
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.OnExistingAllowancePolicy
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.requestResourceAllocation
import io.paritytech.polkadotapp.feature_products_api.domain.deriveEntropy.DeriveEntropyUseCase
import io.paritytech.polkadotapp.feature_products_api.domain.sponsoring.PreimageSubmitSponsoring
import io.paritytech.polkadotapp.feature_products_api.domain.sponsoring.StatementStoreSubmissionSponsoring
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.ProductAccountDerivationUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.allowance.AllowanceKeyUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.allowance.AllowanceResourceKind
import io.paritytech.polkadotapp.feature_products_impl.domain.notifications.NotificationId
import io.paritytech.polkadotapp.feature_products_impl.domain.notifications.ProductNotificationScheduler
import io.paritytech.polkadotapp.feature_products_impl.domain.paymentRequest.PaymentRequestContext
import io.paritytech.polkadotapp.feature_products_impl.domain.paymentRequest.PaymentRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.ProductPermissionGuard
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.DeviceCapabilityType
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermissionDeniedException
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.RemotePermissionRequest
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.PaymentTopUpSource
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.TopUpRequestContext
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.TopUpRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.feature_statement_store_api.data.Statement
import io.paritytech.polkadotapp.feature_statement_store_api.data.StatementStoreService
import io.paritytech.polkadotapp.feature_statement_store_api.data.StatementsPage
import io.paritytech.polkadotapp.feature_statement_store_api.data.TopicFilter
import io.paritytech.polkadotapp.feature_statement_store_api.data.models.StatementStoreMessageProof
import io.paritytech.polkadotapp.feature_statement_store_api.domain.StatementStoreMessageProver
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.TransactionStorageService
import io.paritytech.polkadotapp.feature_transactions.api.data.origins.SignedOrigins
import io.paritytech.polkadotapp.feature_transactions.api.data.origins.signedOriginSr25519PrivateKey
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.UsernameOfAccountUseCase
import io.paritytech.polkadotapp.tools_ipfs_api.IpfsContentLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class HostApiInteractor @Inject constructor(
    private val deriveEntropyUseCase: DeriveEntropyUseCase,
    private val productAccountDerivationUseCase: ProductAccountDerivationUseCase,
    private val usernameOfAccountUseCase: UsernameOfAccountUseCase,
    private val chainRegistry: ChainRegistry,
    private val connectionSecrets: ConnectionSecrets,
    private val getContextualAliasUseCase: GetContextualAliasUseCase,
    private val permissionGuard: ProductPermissionGuard,
    private val accountRepository: AccountRepository,
    private val ipfsContentLookup: IpfsContentLookup,
    private val statementStoreService: StatementStoreService,
    private val statementStoreMessageProverFactory: StatementStoreMessageProver.Factory,
    private val productNotificationPublisher: ProductNotificationPublisher,
    private val productNotificationScheduler: ProductNotificationScheduler,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val externalPaymentService: ExternalPaymentService,
    private val paymentRequestContextHolder: PaymentRequestContextHolder,
    private val topUpRequestContextHolder: TopUpRequestContextHolder,
    private val productsRouter: ProductsRouter,
    private val signedOrigins: SignedOrigins,
    private val accountsProtocol: AccountsProtocol,
    private val allowanceKeyUseCase: AllowanceKeyUseCase,
    private val transactionStorageService: TransactionStorageService,
    private val preimageSubmitSponsoring: PreimageSubmitSponsoring,
    private val statementStoreSubmissionSponsoring: StatementStoreSubmissionSponsoring,
    private val appThemeSelector: AppThemeSelector,
) {
    fun subscribeTheme(): Flow<ProductTheme> {
        return appThemeSelector.selectedTheme.map(PolkadotAppTheme::toProductTheme)
    }

    suspend fun accountGet(callingProductId: ProductId, productAccountId: ProductAccountId): Result<ProductAccountResult> {
        val permission = ProductPermission.AccountAccess(productAccountId.productId)
        if (!permissionGuard.requestPermission(callingProductId, permission)) {
            return Result.failure(ProductPermissionDeniedException(permission))
        }

        return productAccountDerivationUseCase.deriveAccountId(productAccountId).map { publicKey ->
            ProductAccountResult(
                publicKey = publicKey.value.toHexString(withPrefix = true),
            )
        }
    }

    suspend fun getUserId(callingProductId: ProductId): Result<GetUserIdResult> {
        val username = usernameOfAccountUseCase.getUsername()
            .onFailure { Timber.w(it, "host_get_user_id: failed to read username") }
            .getOrNull()
            ?: return Result.failure(UserNotConnectedException)

        val permission = ProductPermission.UserIdentityAccess
        if (!permissionGuard.requestPermission(callingProductId, permission)) {
            return Result.failure(ProductPermissionDeniedException(permission))
        }

        return Result.success(GetUserIdResult(primaryUsername = username.username.getDisplayUsername()))
    }

    suspend fun chainNodes(genesisHash: GenesisHash): Result<List<String>> = runCatching {
        val chain = chainRegistry.getChain(genesisHash)
        chain.nodes.wssNodes()
            .saturateNodeUrls(connectionSecrets)
            .map { it.saturatedUrl }
    }

    suspend fun chainSupported(genesisHash: GenesisHash): Result<Boolean> = runCatching {
        chainRegistry.getChainOrNull(genesisHash) != null
    }

    suspend fun getLegacyAccounts(): Result<List<LegacyAccountResult>> = runCatching {
        // We support no legacy accounts so far
        emptyList()
    }

    suspend fun lookupPreimage(hash: ByteArray): Result<ByteArray> {
        return ipfsContentLookup.lookupRawHash(hash)
    }

    suspend fun submitPreimage(callingProductId: ProductId, data: ByteArray): Result<String> {
        val permission = ProductPermission.RemotePermission.PreimageSubmitAccess
        if (!permissionGuard.consumePermission(callingProductId, permission)) {
            return Result.failure(ProductPermissionDeniedException(permission))
        }

        return preimageSubmitSponsoring.sponsorPreimageSubmit(callingProductId, data.size.bytes)
            .flatMap { key -> signedOrigins.signedOriginSr25519PrivateKey(key.bytes) }
            .flatMap { origin -> transactionStorageService.store(data, origin) }
    }

    // TODO remove once all products have migrated to createStatementProofAuthorized.
    @Deprecated("Use createStatementProofAuthorized — this signs with the wallet account, not the per-product allowance.")
    suspend fun createStatementProof(statementBody: Statement.Body): Result<StatementStoreMessageProof> = runCatching {
        val walletAccount = accountRepository.getWalletAccount()
        val prover = statementStoreMessageProverFactory.createKeyPairProver(walletAccount)
        prover.generateMessageProof(statementBody)
    }

    suspend fun createStatementProofAuthorized(
        callingProductId: ProductId,
        statementBody: Statement.Body,
    ): Result<StatementStoreMessageProof> {
        return statementStoreSubmissionSponsoring.ensureSponsorshipKey(callingProductId)
            .map { Sr25519SubstrateKeypairFactory.createKeypairFromSecret(it.bytes.value) }
            .map { keypair -> statementStoreMessageProverFactory.createKeyPairProver(keypair) }
            .mapCatching { prover -> prover.generateMessageProof(statementBody) }
    }

    suspend fun requestResourceAllocation(
        callingProductId: ProductId,
        resources: List<AllocatableResource>,
    ): Result<List<AllocationOutcome>> = runCatching {
        val apOutcomes = accountsProtocol.requestResourceAllocation(
            callingProduct = callingProductId,
            resources = resources.map { it.toApResource() },
            onExisting = OnExistingAllowancePolicy.INCREASE,
        )
        apOutcomes.forEach { allowanceKeyUseCase.persistIfAllocated(callingProductId, it) }
        apOutcomes.map { it.toTrUApiOutcome() }
    }.logFailure("requestResourceAllocation failed")

    suspend fun publishNotification(
        callingProductId: ProductId,
        text: String,
        deeplink: String?,
        scheduledAt: Instant?,
    ): Result<NotificationId> {
        val permission = ProductPermission.DeviceCapability(DeviceCapabilityType.Notifications)
        if (!permissionGuard.consumePermission(callingProductId, permission)) {
            return Result.failure(ProductPermissionDeniedException(permission))
        }

        return processNotification(callingProductId, text, deeplink, scheduledAt)
    }

    suspend fun cancelNotification(callingProductId: ProductId, notificationId: NotificationId): Result<Unit> {
        val permission = ProductPermission.DeviceCapability(DeviceCapabilityType.Notifications)
        if (!permissionGuard.consumePermission(callingProductId, permission)) {
            return Result.failure(ProductPermissionDeniedException(permission))
        }

        return productNotificationScheduler.cancel(callingProductId, notificationId)
    }

    suspend fun requestDevicePermission(callingProductId: ProductId, capability: DeviceCapabilityType): Result<Boolean> {
        val permission = ProductPermission.DeviceCapability(capability)
        return Result.success(permissionGuard.requestPermission(callingProductId, permission))
    }

    suspend fun requestRemotePermissions(
        callingProductId: ProductId,
        requests: List<RemotePermissionRequest>,
    ): Result<Boolean> = runCatching {
        val permissions = requests.flatMap { it.toDomainPermissions() }
        permissionGuard.requestPermissionsBatched(callingProductId, permissions)
    }

    suspend fun submitStatement(callingProductId: ProductId, statement: Statement): Result<Unit> {
        val permission = ProductPermission.RemotePermission.StatementSubmitAccess
        if (!permissionGuard.consumePermission(callingProductId, permission)) {
            return Result.failure(ProductPermissionDeniedException(permission))
        }

        val signer = statement.proof.publicKey.toDataByteArray()
        return statementStoreSubmissionSponsoring.validateSponsorship(callingProductId, signer)
            .flatMap { statementStoreService.submitStatement(statement) }
    }

    fun subscribeStatements(filter: TopicFilter): Flow<StatementsPage> {
        return statementStoreService.subscribeStatements(filter)
            .mapNotNull { it.getOrNull() }
    }

    suspend fun requestPayment(
        callingProductId: ProductId,
        amount: Balance,
        destination: AccountId,
    ): Result<PaymentId> {
        checkSufficientBalance(callingProductId, amount).onFailure { return Result.failure(it) }

        awaitUserAuthorization(callingProductId, amount, destination)
            .onFailure { return Result.failure(it) }

        return externalPaymentService.initiatePayment(
            origin = callingProductId.value,
            amount = amount,
            destination = destination,
        )
    }

    private suspend fun processNotification(
        callingProductId: ProductId,
        text: String,
        deeplink: String?,
        scheduledAt: Instant?
    ): Result<NotificationId> {
        return if (scheduledAt == null) {
            val id = NotificationId.generate(callingProductId)
            productNotificationPublisher.publishNotification(id.value, text, deeplink)
            Result.success(id)
        } else {
            productNotificationScheduler.schedule(callingProductId, text, deeplink, scheduledAt)
        }
    }

    /**
     * Balance is checked regardless of BalanceAccess — a product may request a payment without
     * holding that permission. The permission only affects which typed error we surface when the
     * balance is insufficient:
     *  - granted → InsufficientBalance (product already knows balances)
     *  - not granted → Rejected (don't leak that the balance is the problem)
     *
     * We use `check` (not `requestPermission`) so the user isn't prompted for BalanceAccess just
     * to error out — the payment prompt is the only user-facing surface in this flow.
     */
    private suspend fun checkSufficientBalance(
        callingProductId: ProductId,
        amount: Balance,
    ): Result<Unit> {
        val balance = totalBalanceUseCase.getBalance().getOrElse { return Result.failure(it) }
        if (balance.spendableBalance.total >= amount) return Result.success(Unit)

        val hasBalanceAccess = permissionGuard.check(callingProductId, ProductPermission.BalanceAccess)
        return if (hasBalanceAccess) {
            Result.failure(InsufficientBalanceException)
        } else {
            Result.failure(PaymentRejectedException)
        }
    }

    private suspend fun awaitUserAuthorization(
        callingProductId: ProductId,
        amount: Balance,
        destination: AccountId,
    ): Result<Unit> {
        val context = PaymentRequestContext(
            productId = callingProductId,
            amount = amount,
            destination = destination,
        )
        paymentRequestContextHolder.set(context)
        productsRouter.openPaymentRequestPrompt()
        val decision = context.awaitDecision()
        return if (decision is PaymentRequestContext.Decision.Rejected) {
            Result.failure(PaymentRejectedException)
        } else {
            Result.success(Unit)
        }
    }

    fun subscribePaymentStatus(
        callingProductId: ProductId,
        paymentId: PaymentId,
    ): Flow<PaymentStatus> {
        return externalPaymentService.subscribePaymentStatus(
            origin = callingProductId.value,
            paymentId = paymentId,
        )
    }

    suspend fun topUp(
        callingProductId: ProductId,
        amount: Balance,
        source: PaymentTopUpSource,
    ): Result<Unit> {
        val contextSource = resolveTopUpSource(callingProductId, source)
            .getOrElse { return Result.failure(it) }

        val context = TopUpRequestContext(
            productId = callingProductId,
            amount = amount,
            source = contextSource,
        )
        topUpRequestContextHolder.set(context)
        productsRouter.openTopUpRequestPrompt()
        return when (val outcome = context.awaitOutcome()) {
            TopUpRequestContext.Outcome.Claimed -> Result.success(Unit)
            is TopUpRequestContext.Outcome.Failed -> Result.failure(outcome.reason)
        }
    }

    private suspend fun resolveTopUpSource(
        callingProductId: ProductId,
        source: PaymentTopUpSource,
    ): Result<TopUpRequestContext.Source> = when (source) {
        is PaymentTopUpSource.ProductAccount -> {
            val productAccountId = ProductAccountId(productId = callingProductId.value, derivationIndex = source.derivationIndex)
            productAccountDerivationUseCase.deriveTransactionSignerSource(productAccountId)
                .map { TopUpRequestContext.Source.Onboard(it) }
        }

        is PaymentTopUpSource.PrivateKey -> signedOrigins.signedTransactionSourceSr25519PrivateKey(source.key)
            .map { TopUpRequestContext.Source.Onboard(it) }

        is PaymentTopUpSource.Coins -> Result.success(TopUpRequestContext.Source.Coins(source.secretKeys))
    }

    suspend fun deriveEntropy(callingProductId: ProductId, key: ByteArray): Result<ByteArray> {
        return deriveEntropyUseCase.deriveEntropy(callingProductId, key)
    }

    fun subscribePaymentBalance(callingProductId: ProductId): Flow<PaymentBalance> = flowOfAll {
        val permission = ProductPermission.BalanceAccess
        if (!permissionGuard.requestPermission(callingProductId, permission)) {
            throw ProductPermissionDeniedException(permission)
        }

        totalBalanceUseCase.subscribeTotalBalance()
            .mapNotNull { it.getOrNull() }
            .map { PaymentBalance(available = it.totalBalance) }
    }

    suspend fun accountGetAlias(callingProductId: ProductId, productAccountId: ProductAccountId): Result<ContextualAlias> {
        val permission = ProductPermission.AccountAccess(productAccountId.productId)
        if (!permissionGuard.requestPermission(callingProductId, permission)) {
            return Result.failure(ProductPermissionDeniedException(permission))
        }

        return getContextualAliasUseCase.getAlias(productAccountId)
    }
}

data class ProductAccountResult(val publicKey: String)

data class LegacyAccountResult(val publicKey: String, val name: String?)

data class GetUserIdResult(val primaryUsername: String)

data class PaymentBalance(val available: Balance)

data class ProductTheme(val name: String, val variant: ThemeVariant)

enum class ThemeVariant { Light, Dark }

private fun PolkadotAppTheme.toProductTheme(): ProductTheme = ProductTheme(
    name = name.replaceFirstChar(Char::lowercaseChar),
    variant = if (colors().bg.surface.main.isLight) ThemeVariant.Light else ThemeVariant.Dark,
)

// Perceived-brightness (Rec. 601 luma) of the surface color
private val Color.isLight: Boolean
    get() = (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f

object InsufficientBalanceException : RuntimeException("insufficient balance")

object PaymentRejectedException : RuntimeException("payment rejected")

class AllowanceDeniedException(val kind: AllowanceResourceKind) :
    RuntimeException("allowance allocation rejected by user for $kind")

class AllowanceUnavailableException(val kind: AllowanceResourceKind) :
    RuntimeException("allowance not available for $kind")

private fun AllocatableResource.toApResource(): ApAllocatableResource = when (this) {
    AllocatableResource.BulletInAllowance -> ApAllocatableResource.BulletInAllowance
    AllocatableResource.StatementStoreAllowance -> ApAllocatableResource.StatementStoreAllowance
    is AllocatableResource.SmartContractAllowance -> ApAllocatableResource.SmartContractAllowance(dest)
    AllocatableResource.AutoSigning -> ApAllocatableResource.AutoSigning
}

private fun ApAllocationOutcome.toTrUApiOutcome(): AllocationOutcome = when (this) {
    is ApAllocationOutcome.Allocated -> AllocationOutcome.Allocated
    ApAllocationOutcome.Rejected -> AllocationOutcome.Rejected
    ApAllocationOutcome.NotAvailable -> AllocationOutcome.NotAvailable
}

/** Message must be literal "NotConnected" — load-bearing token matched by JS-side `handleGetUserId`. */
object UserNotConnectedException : RuntimeException("NotConnected")

private fun RemotePermissionRequest.toDomainPermissions(): List<ProductPermission.RemotePermission> = when (this) {
    is RemotePermissionRequest.Remote -> domains.map { ProductPermission.RemotePermission.NetworkAccess(it) }
    RemotePermissionRequest.WebRtc -> listOf(ProductPermission.RemotePermission.WebRtcAccess)
    RemotePermissionRequest.ChainSubmit -> listOf(ProductPermission.RemotePermission.ChainSubmitAccess)
    RemotePermissionRequest.StatementSubmit -> listOf(ProductPermission.RemotePermission.StatementSubmitAccess)
    RemotePermissionRequest.PreimageSubmit -> listOf(ProductPermission.RemotePermission.PreimageSubmitAccess)
}
