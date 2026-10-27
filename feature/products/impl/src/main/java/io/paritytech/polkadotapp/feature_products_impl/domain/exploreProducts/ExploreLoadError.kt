package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

sealed class ExploreLoadError(cause: Throwable) : Exception(cause) {
    class HostUnresolved(cause: Throwable) : ExploreLoadError(cause)

    class SetupFailed(val kind: ExploreSetupFailure, cause: Throwable) : ExploreLoadError(cause)
}

enum class ExploreSetupFailure {
    NOT_FOUND,
    UNREACHABLE,
    DAMAGED,
    CONNECTION,
    UNKNOWN,
}
