package io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode

import io.paritytech.polkadotapp.common.utils.Urls
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.progressStallReport.markRegion
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import javax.inject.Inject
import io.paritytech.polkadotapp.common.R as RCommon

interface MerchantProductLoader {
    context(diagnostics: StalenessReportCollector)
    suspend fun getMerchantUrl(): Result<String>

    suspend fun warmUpMerchantLoading()
}

class RealMerchantProductLoader @Inject constructor(
    private val merchantDomainProvider: MerchantDomainProvider,
    private val dotNsResolver: DotNsResolver,
) : MerchantProductLoader {
    // Resolves eagerly: the WebView's own resolve failure never surfaces in the session's progress.
    context(diagnostics: StalenessReportCollector)
    override suspend fun getMerchantUrl(): Result<String> = diagnostics.markRegion(RCommon.string.merchant_mode_stall_opening) {
        merchantDomainProvider.getMerchantDomain().flatMap { domain ->
            dotNsResolver.resolveToLocalUri(domain).map { Urls.ensureHasProtocolOrHttps(domain) }
        }
    }

    override suspend fun warmUpMerchantLoading() {
        with(StalenessReportCollector.NoOp) {
            getMerchantUrl().logFailure("Failed to warm up merchant mode loading")
        }
    }
}
