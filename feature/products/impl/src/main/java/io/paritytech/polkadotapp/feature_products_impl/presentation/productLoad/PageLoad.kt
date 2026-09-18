package io.paritytech.polkadotapp.feature_products_impl.presentation.productLoad

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import io.paritytech.polkadotapp.feature_products_api.domain.error.ProductResolutionError
import io.paritytech.polkadotapp.feature_products_api.model.ResolvedProduct

internal sealed interface PageLoad {
    data object NotAProduct : PageLoad
    data object Resolving : PageLoad
    data class Serving(val resolved: ResolvedProduct, val progress: DotNsLoadProgress) : PageLoad
    data class Failed(val error: ProductResolutionError) : PageLoad
}

internal fun PageLoad.toProgress(): DotNsLoadProgress = when (this) {
    PageLoad.NotAProduct -> DotNsLoadProgress.Idle
    PageLoad.Resolving -> DotNsLoadProgress.Resolving
    is PageLoad.Serving -> progress
    is PageLoad.Failed -> DotNsLoadProgress.Failed(error)
}

internal fun PageLoad.Serving.hasAppSurface(): Boolean = resolved.executables.app != null

internal fun Throwable.toProductResolutionError(): ProductResolutionError =
    this as? ProductResolutionError ?: ProductResolutionError.Unknown
