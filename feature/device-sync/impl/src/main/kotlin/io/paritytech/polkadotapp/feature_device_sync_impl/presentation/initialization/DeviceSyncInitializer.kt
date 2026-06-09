package io.paritytech.polkadotapp.feature_device_sync_impl.presentation.initialization

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.AppInitializer
import io.paritytech.polkadotapp.feature_device_sync_api.domain.DeviceSyncCoordinator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DeviceSyncInitializer @Inject constructor(
    private val coordinator: DeviceSyncCoordinator,
) : AppInitializer {
    context(ComputationalScope)
    override fun initialize(): Result<Unit> = runCatching {
        coordinator.startSubscriptions()
    }
}
