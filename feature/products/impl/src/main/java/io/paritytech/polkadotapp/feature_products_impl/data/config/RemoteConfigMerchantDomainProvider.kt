package io.paritytech.polkadotapp.feature_products_impl.data.config

import io.paritytech.polkadotapp.common.utils.Urls
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode.MerchantDomainProvider
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import javax.inject.Inject

internal class RemoteConfigMerchantDomainProvider @Inject constructor(
    private val remoteConfigService: RemoteConfigService,
) : MerchantDomainProvider {
    // No sync retry outlives the splash, so a failed launch sync leaves this waiting.
    override suspend fun getMerchantDomain(): Result<String> =
        remoteConfigService.getSyncedString(CONFIG_KEY)
            .mapCatching { it.toDomain() }
            .logFailure("Merchant mode has no usable $CONFIG_KEY")

    private fun String.toDomain(): String = Urls.hostOf(Urls.ensureHasProtocolOrHttps(this))
        .ifBlank { error("Remote Config $CONFIG_KEY carries no merchant domain") }

    private companion object {
        const val CONFIG_KEY = "merchant_url"
    }
}
