package io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import kotlinx.coroutines.flow.StateFlow

interface ConnectionStatusMixin {
    val bannerModel: StateFlow<ConnectionStatusBannerModel>

    interface Factory {
        fun create(scope: ComputationalScope): ConnectionStatusMixin
    }
}
