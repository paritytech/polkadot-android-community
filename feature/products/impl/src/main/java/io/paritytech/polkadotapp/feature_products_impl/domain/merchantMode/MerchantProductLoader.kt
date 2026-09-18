package io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode

import androidx.core.net.toUri
import io.paritytech.polkadotapp.common.utils.Urls
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.progressStallReport.markRegion
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_api.domain.getTldRetrying
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.ResolvedProduct
import io.paritytech.polkadotapp.feature_products_impl.domain.usecase.ResolveProductUseCase
import javax.inject.Inject
import io.paritytech.polkadotapp.common.R as RCommon

data class MerchantProduct(val url: String, val resolved: ResolvedProduct)

interface MerchantProductLoader {
    context(diagnostics: StalenessReportCollector)
    suspend fun openMerchantProduct(): Result<MerchantProduct>

    suspend fun warmUpMerchantLoading()
}

class RealMerchantProductLoader @Inject constructor(
    private val merchantDomainProvider: MerchantDomainProvider,
    private val dotNsResolver: DotNsResolver,
    private val dotNsTldProvider: DotNsTldProvider,
    private val resolveProductUseCase: ResolveProductUseCase,
) : MerchantProductLoader {
    context(diagnostics: StalenessReportCollector)
    override suspend fun openMerchantProduct(): Result<MerchantProduct> =
        diagnostics.markRegion(RCommon.string.merchant_mode_stall_opening) {
            merchantUrl().flatMap { url -> resolveProduct(url).map { MerchantProduct(url, it) } }
        }

    // The session resolves the same domain again on demand; this only primes the cache.
    override suspend fun warmUpMerchantLoading() {
        merchantDomainProvider.getMerchantDomain()
            .flatMap { dotNsResolver.resolveToLocalUri(it) }
            .logFailure("Failed to warm up merchant mode loading")
    }

    private suspend fun merchantUrl(): Result<String> =
        merchantDomainProvider.getMerchantDomain().map(Urls::ensureHasProtocolOrHttps)

    context(diagnostics: StalenessReportCollector)
    private suspend fun resolveProduct(url: String): Result<ResolvedProduct> =
        diagnostics.markRegion(RCommon.string.stall_reading_chain_state) {
            ProductId.fromUrl(url.toUri(), dotNsTldProvider.getTldRetrying())
                .flatMap { resolveProductUseCase.resolve(it) }
        }
}
