package io.paritytech.polkadotapp.feature_products_impl.domain.webView

import io.paritytech.polkadotapp.common.utils.toCanonicalDotHost
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_api.presentation.DotNsDevOriginResolver
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import javax.inject.Inject

/**
 * Debug builds: serves a product's app from a developer's local server instead of its archive.
 *
 * Only the canonical base host is overridden — that is the origin the SPA runs under. `app.<base>`
 * and `worker.<base>` name executables of their own, which keep their published archives.
 */
class ProductDevOriginResolver @Inject constructor(
    private val debugAppOrigins: DebugAppOrigins,
    private val dotNsTldProvider: DotNsTldProvider,
) : DotNsDevOriginResolver {
    override suspend fun devOriginFor(requestHost: String): String? {
        // Settled by the time a request is intercepted, since the client awaits it first.
        val tld = dotNsTldProvider.getTld().getOrNull() ?: return null
        // Origins are keyed by the canonical dotNS name, so a `.dot.li` mirror maps to it as well.
        val host = requestHost.toCanonicalDotHost()
        val productId = ProductId.fromString(host, tld).getOrNull() ?: return null
        if (!productId.value.equals(host, ignoreCase = true)) return null

        return debugAppOrigins.get(productId)
    }
}
