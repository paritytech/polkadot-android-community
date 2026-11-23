package io.paritytech.polkadotapp.feature_chats_api.domain.interactors

interface PendingPaymentsUseCase {
    data class PendingPayments(val receiving: Int, val sending: Int) {
        val isEmpty: Boolean get() = receiving == 0 && sending == 0
    }

    suspend fun countPendingPayments(): PendingPayments
}
