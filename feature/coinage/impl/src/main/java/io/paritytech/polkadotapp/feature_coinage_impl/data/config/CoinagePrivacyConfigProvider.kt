package io.paritytech.polkadotapp.feature_coinage_impl.data.config

import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinagePrivacyConfig
import io.paritytech.polkadotapp.feature_coinage_impl.data.mappers.toDomain
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import io.paritytech.polkadotapp.tools_remoteconfig_api.getJsonObject
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface CoinagePrivacyConfigProvider {
    suspend fun privacyConfigOrDefault(): CoinagePrivacyConfig
}

class RemoteConfigCoinagePrivacyConfigProvider @Inject constructor(
    private val remoteConfigService: RemoteConfigService,
    private val dispatchers: CoroutineDispatchers,
) : CoinagePrivacyConfigProvider {
    override suspend fun privacyConfigOrDefault(): CoinagePrivacyConfig = withContext(dispatchers.io) {
        // An unset key reads back as an empty string, which Gson parses to null.
        remoteConfigService.getJsonObject<CoinagePrivacyConfigRemote?>(CONFIG_KEY)
            .logFailure("Can't read the coinage privacy config, using the bundled defaults")
            .getOrNull()
            .toDomain()
    }

    private companion object {
        const val CONFIG_KEY = "coinage_privacy_config"
    }
}
