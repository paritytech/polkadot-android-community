package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolutionError
import io.paritytech.polkadotapp.feature_products_api.domain.error.ProductResolutionError

internal fun classifyProductFailure(error: Throwable): ExploreSetupFailure {
    return when (error) {
        ProductResolutionError.MalformedManifest -> ExploreSetupFailure.DAMAGED
        else -> ExploreSetupFailure.UNKNOWN
    }
}

internal fun classifyContentFailure(error: Throwable): ExploreSetupFailure {
    return when (error) {
        is DotNsResolutionError.NotFound -> ExploreSetupFailure.NOT_FOUND
        is DotNsResolutionError.RegistryUnreachable -> ExploreSetupFailure.UNREACHABLE
        is DotNsResolutionError.InvalidContentHash, is DotNsResolutionError.ContentDamaged -> ExploreSetupFailure.DAMAGED
        is DotNsResolutionError.GatewayUnreachable -> ExploreSetupFailure.CONNECTION
        else -> ExploreSetupFailure.UNKNOWN
    }
}
