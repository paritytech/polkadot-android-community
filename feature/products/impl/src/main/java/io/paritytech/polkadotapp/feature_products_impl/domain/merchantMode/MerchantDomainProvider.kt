package io.paritytech.polkadotapp.feature_products_impl.domain.merchantMode

interface MerchantDomainProvider {
    suspend fun getMerchantDomain(): Result<String>
}
