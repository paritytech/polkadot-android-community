package io.paritytech.polkadotapp.tools_remoteconfig_impl.presentation

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.AppInitializer
import io.paritytech.polkadotapp.common.utils.network.NetworkStateService
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RemoteConfigSyncInitializer @Inject constructor(
    private val remoteConfigService: RemoteConfigService,
    private val networkStateService: NetworkStateService,
) : AppInitializer {
    context(scope: ComputationalScope)
    override fun initialize(): Result<Unit> = runCatching {
        scope.launch { syncWhenOnline() }
    }

    // Every getSynced* read suspends until a sync succeeds, so a cold start without network must keep trying
    // for the rest of the process rather than give up after one attempt.
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun syncWhenOnline() {
        networkStateService.isNetworkAvailable
            .transformLatest { isAvailable ->
                if (isAvailable) {
                    syncWithBackoff()
                    emit(Unit)
                }
            }
            .first()
    }

    private suspend fun syncWithBackoff() {
        var retryDelay = INITIAL_RETRY_DELAY
        while (remoteConfigService.sync().isFailure) {
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
        }
    }

    private companion object {
        val INITIAL_RETRY_DELAY = 2.seconds
        val MAX_RETRY_DELAY = 1.minutes
    }
}
