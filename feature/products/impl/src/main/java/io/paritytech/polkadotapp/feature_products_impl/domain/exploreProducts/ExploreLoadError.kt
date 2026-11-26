package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

sealed class ExploreLoadError(cause: Throwable) : Exception(cause) {
    class HostUnresolved(cause: Throwable) : ExploreLoadError(cause)

    class SetupFailed(cause: Throwable) : ExploreLoadError(cause)
}
