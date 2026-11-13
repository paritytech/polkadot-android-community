package io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling

import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinagePrivacyConfig
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingParams
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.params
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.CoinagePrivacyConfigProvider

val RecyclingStrategyType.bundledParams: RecyclingParams
    get() = params(CoinagePrivacyConfig.BUNDLED)

class FakeCoinagePrivacyConfigProvider : CoinagePrivacyConfigProvider {
    var config: CoinagePrivacyConfig = CoinagePrivacyConfig.BUNDLED

    override suspend fun privacyConfigOrDefault(): CoinagePrivacyConfig = config
}
