package io.paritytech.polkadotapp.feature_coinage_impl.domain.worker

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageRecyclingSyncManager
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageRecyclingUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

class RealCoinageRecyclingSyncManager @Inject constructor(
    private val contextManager: ContextManager,
    private val coinageRecyclingUseCase: CoinageRecyclingUseCase
) : CoinageRecyclingSyncManager {
    context(ComputationalScope)
    override fun recycleAndSchedule() {
        launch { coinageRecyclingUseCase() }
        CoinageRecyclingWorker.schedule(contextManager.applicationContext)
    }
}
