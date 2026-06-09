package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import javax.inject.Inject

interface ExploreProductsService {
    fun getExploreUrl(): String

    suspend fun warmUpExploreLoading()
}

class RealExploreProductsService @Inject constructor(
    private val dotNsResolver: DotNsResolver,
) : ExploreProductsService {
    companion object {
        private const val BROWSE_DOT_HOST = "browse.dot"
        private const val BROWSE_DOT_URL = "https://${BROWSE_DOT_HOST}"
    }

    override fun getExploreUrl(): String {
        return BROWSE_DOT_URL
    }

    override suspend fun warmUpExploreLoading() {
        dotNsResolver.resolveToLocalUri(BROWSE_DOT_HOST)
            .logFailure("Failed to warm up explore loading")
    }
}
