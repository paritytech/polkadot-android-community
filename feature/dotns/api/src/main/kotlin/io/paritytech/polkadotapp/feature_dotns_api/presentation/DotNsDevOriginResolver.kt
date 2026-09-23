package io.paritytech.polkadotapp.feature_dotns_api.presentation

/** Debug builds: the origin a product's content is fetched from instead of its archive, e.g. `http://127.0.0.1:5183`. */
fun interface DotNsDevOriginResolver {
    /** The dev origin for [requestHost] (scheme + host + port, no trailing slash), or null to serve the archive. */
    suspend fun devOriginFor(requestHost: String): String?

    companion object {
        val None = DotNsDevOriginResolver { null }
    }
}
