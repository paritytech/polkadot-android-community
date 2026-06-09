package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.utils.stateInBackground
import io.paritytech.polkadotapp.feature_connection_status_api.domain.ConnectionStatusMonitor
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ConnectionStatus
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ConnectionStatusBannerModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ConnectionStatusMixin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

internal class RealConnectionStatusMixin(
    scope: ComputationalScope,
    monitor: ConnectionStatusMonitor,
) : ConnectionStatusMixin, ComputationalScope by scope {
    override val bannerModel: StateFlow<ConnectionStatusBannerModel> = monitor.observeStatus()
        .map(ConnectionStatus::toUi)
        .stateInBackground(
            started = SharingStarted.WhileSubscribed(),
            initialValue = ConnectionStatusBannerModel.None,
        )
}

private fun ConnectionStatus.toUi(): ConnectionStatusBannerModel = when (this) {
    ConnectionStatus.Offline -> ConnectionStatusBannerModel.WaitingForNetwork
    is ConnectionStatus.Connecting -> if (retrying) {
        ConnectionStatusBannerModel.Connecting(connectedChains, totalChains)
    } else {
        ConnectionStatusBannerModel.None
    }
    ConnectionStatus.Connected -> ConnectionStatusBannerModel.None
}
