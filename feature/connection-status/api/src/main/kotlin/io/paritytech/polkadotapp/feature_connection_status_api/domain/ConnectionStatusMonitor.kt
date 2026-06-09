package io.paritytech.polkadotapp.feature_connection_status_api.domain

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.Flow

interface ConnectionStatusMonitor {
    fun observeStatus(): Flow<ConnectionStatus>
}
