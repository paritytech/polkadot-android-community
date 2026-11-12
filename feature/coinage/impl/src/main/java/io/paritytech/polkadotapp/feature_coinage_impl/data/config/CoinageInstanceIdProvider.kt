package io.paritytech.polkadotapp.feature_coinage_impl.data.config

import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstanceId
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class CoinageInstanceIdProvider @Inject constructor(
    private val remoteConfigService: RemoteConfigService,
) {
    suspend fun instanceId(): Result<CoinageInstanceId> {
        return remoteConfigService.getSyncedString(INSTANCE_ID_KEY).mapCatching { raw ->
            raw.toUIntOrNull() ?: error("Malformed coinage instance id: $raw")
        }
    }

    private companion object {
        const val INSTANCE_ID_KEY = "coinage_instance_id"
    }
}

suspend fun CoinageInstanceIdProvider.instanceIdOrNull(): CoinageInstanceId? =
    instanceId()
        .onFailure { if (it is CancellationException) throw it }
        .logFailure("Can't read the configured coinage instance")
        .getOrNull()
