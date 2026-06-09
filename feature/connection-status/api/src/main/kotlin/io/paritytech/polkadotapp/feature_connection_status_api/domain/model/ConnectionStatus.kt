package io.paritytech.polkadotapp.feature_connection_status_api.domain.model

sealed interface ConnectionStatus {
    data object Offline : ConnectionStatus
    data class Connecting(
        val retrying: Boolean,
        val connectedChains: Int,
        val totalChains: Int,
    ) : ConnectionStatus
    data object Connected : ConnectionStatus
}
