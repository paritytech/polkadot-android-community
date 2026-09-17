package io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode

import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector

interface MerchantDomainProvider {
    context(diagnostics: StalenessReportCollector)
    suspend fun getMerchantDomain(): Result<String>
}
