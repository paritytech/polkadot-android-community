package io.paritytech.polkadotapp.feature_chats_impl.domain.extension

import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.ChatExtension
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.ChatExtensionContext
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.markMessageProcessed
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment.Status
import io.paritytech.polkadotapp.feature_chats_api.domain.model.isIncoming
import io.paritytech.polkadotapp.feature_chats_api.domain.model.paymentContentOrNull
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.CoinageTransferDetectionRepository
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.isTerminal
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageTransferUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// TODO CoinagePaymentProcessingExtension should maintain its own invariant of never allowing to process the same message twice at the same time
// This generally should not happen given the current ContactChatSession behavior (we don't overwrite messages)
// But the extension should not rely on that and maintain its own invariant, to provide better resilience
class CoinagePaymentProcessingExtension @Inject constructor(
    private val coinageTransferUseCase: CoinageTransferUseCase,
    private val coinageTransferDetectionRepository: CoinageTransferDetectionRepository,
) : ChatExtension {
    override val id = "CoinagePaymentProcessing"

    override val activationStateExternallyControlled: Boolean = false

    context(ChatExtensionContext)
    override fun startGlobalWork() {
        // Retry unfinished work from previous session
        scope.launch {
            getUnprocessedMessages(contentTypes = listOf(CoinagePayment::class))
                .forEach { message -> processPayment(message) }
        }

        // Subscribe to ALL new payment messages (any room, background)
        subscribeNewMessages(contentTypes = listOf(CoinagePayment::class))
            .onEach { message -> processPayment(message) }
            .launchIn(scope)
    }

    context(ChatExtensionContext)
    private fun processPayment(message: ChatMessage) {
        val content = message.paymentContentOrNull() ?: return

        Timber.tag("CoinageTransfer").d("Start processing for message ${message.id}")

        scope.launch {
            val pastDetection = coinageTransferDetectionRepository.getCoinageTransferDetection(message.id)
            when {
                pastDetection?.isTerminal() == true -> {
                    modifyPaymentContent(message, pastDetection)
                    markMessageProcessed(message)
                    return@launch
                }

                else -> proceedDetection(message, content, pastDetection)
            }
        }
    }

    context(ChatExtensionContext)
    private suspend fun proceedDetection(
        chatMessage: ChatMessage,
        content: CoinagePayment,
        pastDetection: CoinageTransferDetection?
    ) {
        coinageTransferUseCase(
            transferCoins = chatMessage.isIncoming,
            coinKeys = content.coinKeys.map { it.toDataByteArray() },
            pastDetection = pastDetection
        )
            .collect { detection ->
                modifyPaymentContent(chatMessage, detection)

                if (detection !is CoinageTransferDetection.Detecting) {
                    coinageTransferDetectionRepository.saveCoinageTransferDetection(chatMessage.id, detection)
                }

                if (detection.isTerminal()) {
                    markMessageProcessed(chatMessage)
                }
            }
    }

    context(ChatExtensionContext)
    private suspend fun modifyPaymentContent(message: ChatMessage, detection: CoinageTransferDetection) {
        val content = message.paymentContentOrNull() ?: return

        val newContent = content.copy(status = detection.toPaymentStatus())

        modifyMessage(message.chatId, message.id, newContent)
    }

    private fun CoinageTransferDetection.toPaymentStatus(): Status {
        return when (this) {
            is CoinageTransferDetection.Detecting -> Status.Detecting
            is CoinageTransferDetection.Detected -> Status.Detected(amount)
            is CoinageTransferDetection.Transferred -> Status.Transferred(amount)
            is CoinageTransferDetection.Error.Detection -> Status.FailedDetection
            is CoinageTransferDetection.Error.Transfer -> Status.FailedTransfer
        }
    }
}
