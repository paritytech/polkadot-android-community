package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import javax.inject.Inject

interface ExploreProductsService {
    suspend fun getExploreProductId(): Result<ProductId>

    suspend fun warmUpExploreLoading()
}

class RealExploreProductsService @Inject constructor(
    private val dotNsResolver: DotNsResolver,
    private val dotNsTldProvider: DotNsTldProvider,
) : ExploreProductsService {
    companion object {
        private const val BROWSE_LABEL = "browse"
    }

    override suspend fun getExploreProductId(): Result<ProductId> {
        return dotNsTldProvider.getTld().flatMap { tld -> ProductId.fromString("$BROWSE_LABEL${tld.suffix}", tld) }
    }

    override suspend fun warmUpExploreLoading() {
        dotNsTldProvider.getTld()
            .flatMap { tld -> dotNsResolver.resolveToLocalUri("$BROWSE_LABEL${tld.suffix}") }
            .logFailure("Failed to warm up explore loading")
    }
}
