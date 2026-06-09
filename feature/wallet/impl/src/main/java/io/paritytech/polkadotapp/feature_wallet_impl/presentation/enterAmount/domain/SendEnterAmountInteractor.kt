package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.util.planksFromAmount
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.validation.Validation
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.filterResultSuccessNotNull
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.mapResult
import io.paritytech.polkadotapp.feature_chats_api.domain.ChatMessageSender
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentPlanner
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentService
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.awaitTransferOutcome
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferMemo
import io.paritytech.polkadotapp.feature_coinage_api.domain.submitter.CoinsSubmitter
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.PrepareCoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ValidateTransferPlanUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.prepareMemo
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.origins.FreeTransactionOrigins
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.Fee
import io.paritytech.polkadotapp.feature_transfers_api.data.type.TokenTransfersTypeRegistry
import io.paritytech.polkadotapp.feature_transfers_api.domain.model.TransferArguments
import io.paritytech.polkadotapp.feature_wallet_impl.domain.model.AvailableToSendAmount
import io.paritytech.polkadotapp.feature_wallet_impl.domain.model.SendPlan
import io.paritytech.polkadotapp.feature_wallet_impl.domain.model.TransferMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

interface SendEnterAmountInteractor {
    val sendValidation: Validation<SendValidationPayload>

    fun tokenBalance(): Flow<AvailableToSendAmount>

    suspend fun asset(): Chain.Asset

    suspend fun estimateFee(recipient: AccountId, value: BigDecimal): Result<Fee>

    suspend fun send(
        value: BigDecimal,
        trackTransfer: Boolean,
        transferMethod: TransferMethod,
    ): Result<Unit>

    suspend fun plan(value: BigDecimal, transferMethod: TransferMethod): SendPlan?
}

class RealSendEnterAmountInteractor @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val transfersTypeRegistry: TokenTransfersTypeRegistry,
    private val freeTransactionOrigins: FreeTransactionOrigins,
    private val chatMessageSender: ChatMessageSender,
    private val prepareCoinageTransferUseCase: PrepareCoinageTransferUseCase,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val externalPaymentService: ExternalPaymentService,
    private val validateTransferPlanUseCase: ValidateTransferPlanUseCase,
    private val externalPaymentPlanner: ExternalPaymentPlanner,
    private val coinsSubmitters: Map<String, @JvmSuppressWildcards CoinsSubmitter>,
    private val coroutineDispatchers: CoroutineDispatchers,
    override val sendValidation: SendValidation
) : SendEnterAmountInteractor {
    companion object {
        private const val WALLET_PAYMENT_ORIGIN = "native-payment"
    }

    override suspend fun asset(): Chain.Asset = chainAssetProvider.asset()

    override fun tokenBalance(): Flow<AvailableToSendAmount> {
        return totalBalanceUseCase.subscribeTotalBalance()
            .mapResult { AvailableToSendAmount(it.spendableBalance, asset()) }
            .filterResultSuccessNotNull()
    }

    override suspend fun estimateFee(recipient: AccountId, value: BigDecimal): Result<Fee> {
        val asset = asset()

        return transfersTypeRegistry.typeFor(asset)
            .calculateFee(
                TransferArguments(
                    recipient = recipient,
                    amount = value.planksFromAmount(asset.precision),
                    origin = freeTransactionOrigins.freeTxFromWalletOrSigned(asset.chainId)
                )
            )
    }

    override suspend fun send(
        value: BigDecimal,
        trackTransfer: Boolean,
        transferMethod: TransferMethod,
    ): Result<Unit> = withContext(coroutineDispatchers.computation) {
        when (transferMethod) {
            is TransferMethod.CoinsViaChat -> sendCoinage(transferMethod.recipient, value)
            is TransferMethod.UnloadIntoExternal -> sendExternalPayment(transferMethod.recipient, value)
            is TransferMethod.CoinsViaSubmitter -> sendViaSubmitter(transferMethod, value)
        }
    }

    override suspend fun plan(value: BigDecimal, transferMethod: TransferMethod): SendPlan? = withContext(coroutineDispatchers.computation) {
        when (transferMethod) {
            is TransferMethod.CoinsViaChat,
            is TransferMethod.CoinsViaSubmitter -> validateTransferPlanUseCase.validate(value)?.let(SendPlan::Coinage)

            is TransferMethod.UnloadIntoExternal -> {
                val amount = asset().planksFromAmount(value)
                externalPaymentPlanner.plan(amount).getOrNull()?.let(SendPlan::External)
            }
        }
    }

    private suspend fun sendCoinage(recipient: AccountId, value: BigDecimal): Result<Unit> {
        return prepareCoinageTransferUseCase.prepareMemo(value)
            .map { transferMemo -> sendChatMessage(recipient, transferMemo) }
            .coerceToUnit()
            .onSuccess { Timber.d("CoinageTransfer: Successful") }
            .logFailure("Coinage transfer failed")
    }

    private suspend fun sendViaSubmitter(
        method: TransferMethod.CoinsViaSubmitter,
        value: BigDecimal,
    ): Result<Unit> {
        val submitter = coinsSubmitters[method.submitterId]
            ?: return Result.failure(IllegalStateException("No CoinsSubmitter registered for '${method.submitterId}'"))

        return prepareCoinageTransferUseCase.prepareMemo(value)
            .flatMap { memo -> submitter.submit(memo, value, method.submitterPayload) }
            .logFailure("Coins submission via '${method.submitterId}' failed")
    }

    private suspend fun sendExternalPayment(recipient: AccountId, value: BigDecimal): Result<Unit> {
        val amount = asset().planksFromAmount(value)

        return externalPaymentService.initiatePayment(
            origin = WALLET_PAYMENT_ORIGIN,
            amount = amount,
            destination = recipient,
        )
            .flatMap { paymentId -> externalPaymentService.awaitTransferOutcome(WALLET_PAYMENT_ORIGIN, paymentId) }
            .logFailure("External payment failed")
    }

    private suspend fun sendChatMessage(
        recipient: AccountId,
        transferMemo: TransferMemo
    ) {
        val chatId = ChatId.fromContact(recipient)
        val content = ChatMessage.Content.CoinagePayment(
            totalValue = transferMemo.totalValue,
            coinKeys = transferMemo.coins.map { it.privateKey.value },
            status = ChatMessage.Content.CoinagePayment.Status.Detecting
        )
        chatMessageSender.sendUserMessage(
            chatId = chatId,
            content = content
        )
    }
}
