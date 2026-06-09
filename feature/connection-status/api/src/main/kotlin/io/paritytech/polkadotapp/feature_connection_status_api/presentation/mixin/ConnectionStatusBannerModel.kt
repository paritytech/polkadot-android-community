package io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin

sealed interface ConnectionStatusBannerModel {
    data object WaitingForNetwork : ConnectionStatusBannerModel
    data class Connecting(
        val connectedChains: Int,
        val totalChains: Int,
    ) : ConnectionStatusBannerModel
    data object None : ConnectionStatusBannerModel
}
