package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_connection_status_api.domain.ConnectionStatusMonitor
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ConnectionStatusMixin
import javax.inject.Inject

class RealConnectionStatusMixinFactory @Inject constructor(
    private val monitor: ConnectionStatusMonitor,
) : ConnectionStatusMixin.Factory {
    override fun create(scope: ComputationalScope): ConnectionStatusMixin =
        RealConnectionStatusMixin(scope, monitor)
}
