package io.paritytech.polkadotapp.feature_products_impl.data.config

import io.paritytech.polkadotapp.common.data.memory.SingleValueCache
import io.paritytech.polkadotapp.common.data.memory.getCatching
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_products_api.domain.FundingDomainProvider
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import javax.inject.Inject

internal class RemoteConfigFundingDomainProvider @Inject constructor(
    private val remoteConfigService: RemoteConfigService,
    private val dotNsTldProvider: DotNsTldProvider,
) : FundingDomainProvider {
    // Unwrapping inside the compute keeps a failed read out of the cache; getCatching re-wraps the throw.
    private val fundingDomainCache = SingleValueCache {
        // Remote Config answers an unset key with an empty string rather than failing, and a blank
        // label would resolve to the bare TLD - a domain the funding product is not served under.
        remoteConfigService.getSyncedString(CONFIG_KEY).getOrThrow()
            .ifBlank { error("Remote Config carries no $CONFIG_KEY") }
    }

    override suspend fun getFundingDomain(): Result<String> = fundingDomainCache.getCatching()

    override suspend fun getFundingProductId(): Result<ProductId> {
        return getFundingDomain().flatMap { domain ->
            dotNsTldProvider.getTld().map { tld -> ProductId.fromStoredValue(domain + tld.suffix) }
        }
    }

    private companion object {
        const val CONFIG_KEY = "funding_domain"
    }
}
