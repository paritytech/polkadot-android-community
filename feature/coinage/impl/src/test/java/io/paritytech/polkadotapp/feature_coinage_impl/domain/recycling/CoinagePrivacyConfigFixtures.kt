package io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling

import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinagePrivacyConfig
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingParams
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.params
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.CoinagePrivacyConfigProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.mappers.BUNDLED_PRIVACY_CONFIG

val RecyclingStrategyType.bundledParams: RecyclingParams
    get() = params(BUNDLED_PRIVACY_CONFIG)

class FakeCoinagePrivacyConfigProvider : CoinagePrivacyConfigProvider {
    var config: CoinagePrivacyConfig = BUNDLED_PRIVACY_CONFIG

    override suspend fun privacyConfigOrDefault(): CoinagePrivacyConfig = config
}
