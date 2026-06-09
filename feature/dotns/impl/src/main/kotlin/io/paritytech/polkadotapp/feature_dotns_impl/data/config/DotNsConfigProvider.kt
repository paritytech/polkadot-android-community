package io.paritytech.polkadotapp.feature_dotns_impl.data.config

import io.paritytech.polkadotapp.common.domain.model.hexToDataByteArray
import io.paritytech.polkadotapp.feature_dotns_impl.data.contract.DotNsConfig
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import io.paritytech.polkadotapp.tools_remoteconfig_api.getSyncedJsonObject
import javax.inject.Inject

interface DotNsConfigProvider {
    suspend fun getDotNsConfig(): Result<DotNsConfig>
}

internal class RemoteConfigDotNsConfigProvider @Inject constructor(
    private val remoteConfigService: RemoteConfigService,
) : DotNsConfigProvider {
    private companion object {
        const val CONFIG_KEY = "dot_ns_config"
    }

    override suspend fun getDotNsConfig(): Result<DotNsConfig> {
        return remoteConfigService.getSyncedJsonObject<DotNsConfigRemote>(CONFIG_KEY)
            .mapCatching { it.toDomain() }
    }

    private fun DotNsConfigRemote.toDomain(): DotNsConfig {
        return DotNsConfig(resolverContractAddress.hexToDataByteArray())
    }
}
