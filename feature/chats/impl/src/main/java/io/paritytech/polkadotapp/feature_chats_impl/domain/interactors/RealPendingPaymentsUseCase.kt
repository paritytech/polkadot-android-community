package io.paritytech.polkadotapp.feature_chats_impl.domain.interactors

import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.PendingPaymentsUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.PendingPaymentsUseCase.PendingPayments
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.isIncoming
import io.paritytech.polkadotapp.feature_chats_impl.data.repository.ChatMessageRepository
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.deriveKeypair
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageStateSyncUseCase
import javax.inject.Inject
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment.Status as PaymentStatus

class RealPendingPaymentsUseCase @Inject constructor(
    private val chatMessageRepository: ChatMessageRepository,
    private val coinageStateSyncUseCase: CoinageStateSyncUseCase,
) : PendingPaymentsUseCase {
    override suspend fun countPendingPayments(): PendingPayments {
        val payments = chatMessageRepository.getPaymentsUpdatedAfter(updatedAfter = null)

        return PendingPayments(
            receiving = payments.count { it.isIncoming && it.paymentContent()?.status?.isUnsettled() == true },
            sending = payments.count { !it.isIncoming && it.isSendingFromThisInstallation() },
        )
    }

    private suspend fun ChatMessage.isSendingFromThisInstallation(): Boolean {
        val payment = paymentContent()?.takeIf { it.status == PaymentStatus.Detecting } ?: return false
        val coins = payment.coinKeys.map { key -> key.toDataByteArray().deriveKeypair().publicKey.intoAccountId() }

        return coinageStateSyncUseCase.isHandedOffFromThisInstallation(coins)
    }

    private fun ChatMessage.paymentContent(): ChatMessage.Content.CoinagePayment? = content as? ChatMessage.Content.CoinagePayment

    private fun PaymentStatus.isUnsettled(): Boolean = when (this) {
        PaymentStatus.Detecting, is PaymentStatus.Detected, is PaymentStatus.PartiallyClaimed -> true
        is PaymentStatus.Transferred, PaymentStatus.FailedDetection, PaymentStatus.FailedTransfer -> false
    }
}
