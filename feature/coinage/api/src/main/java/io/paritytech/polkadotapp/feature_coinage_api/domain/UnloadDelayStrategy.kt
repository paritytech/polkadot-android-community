package io.paritytech.polkadotapp.feature_coinage_api.domain

import io.paritytech.polkadotapp.common.domain.model.Timestamp

interface UnloadDelayStrategy {
    fun calculateDelayUnloadUntil(): Timestamp
}
