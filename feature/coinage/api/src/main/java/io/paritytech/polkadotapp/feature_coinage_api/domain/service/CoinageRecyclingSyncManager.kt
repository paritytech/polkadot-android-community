package io.paritytech.polkadotapp.feature_coinage_api.domain.service

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope

interface CoinageRecyclingSyncManager {
    context(ComputationalScope)
    fun recycleAndSchedule()
}
