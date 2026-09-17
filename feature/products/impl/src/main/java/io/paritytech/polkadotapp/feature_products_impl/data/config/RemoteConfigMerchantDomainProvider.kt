package io.paritytech.polkadotapp.feature_products_impl.data.config

import io.paritytech.polkadotapp.common.utils.Urls
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.progressStallReport.markRegion
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode.MerchantDomainProvider
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import javax.inject.Inject
import io.paritytech.polkadotapp.common.R as RCommon

internal class RemoteConfigMerchantDomainProvider @Inject constructor(
    private val remoteConfigService: RemoteConfigService,
    private val dotNsTldProvider: DotNsTldProvider,
) : MerchantDomainProvider {
    context(diagnostics: StalenessReportCollector)
    override suspend fun getMerchantDomain(): Result<String> {
        // A configured domain already carries its TLD, so it must not be gated on reading one from the chain.
        configuredHost()?.let { return Result.success(it) }

        return diagnostics.markRegion(RCommon.string.stall_reading_chain_state) {
            dotNsTldProvider.getTld().map { tld -> "$DEFAULT_LABEL${tld.suffix}" }
        }
    }

    // Read unsynced: the only sync retry loop dies with the splash (SplashInteractor), so a fetch that fails
    // after it is never retried and a synced read would park for the rest of the process. Unset reads empty.
    private suspend fun configuredHost(): String? {
        val configured = remoteConfigService.getString(CONFIG_KEY)
            .logFailure("Failed to read $CONFIG_KEY")
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return runCatching { Urls.hostOf(Urls.ensureHasProtocolOrHttps(configured)) }
            .logFailure("Remote Config $CONFIG_KEY is not a domain: $configured")
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val CONFIG_KEY = "merchant_url"
        const val DEFAULT_LABEL = "terminal"
    }
}
