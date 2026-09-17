package io.paritytech.polkadotapp.common.utils.network

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.AppInitializer
import io.paritytech.polkadotapp.common.presentation.AppLifecycleObserver
import io.paritytech.polkadotapp.common.presentation.subscribeIsForeground
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

internal class NetworkStateRefreshInitializer @Inject constructor(
    private val networkStateService: NetworkStateService,
    private val appLifecycleObserver: AppLifecycleObserver,
) : AppInitializer {
    context(scope: ComputationalScope)
    override fun initialize(): Result<Unit> = runCatching {
        appLifecycleObserver.subscribeIsForeground()
            .filter { inForeground -> inForeground }
            .onEach { networkStateService.refresh() }
            .launchIn(scope)
    }
}
