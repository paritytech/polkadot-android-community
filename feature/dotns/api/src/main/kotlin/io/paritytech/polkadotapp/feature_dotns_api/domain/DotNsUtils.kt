package io.paritytech.polkadotapp.feature_dotns_api.domain

import android.net.Uri
import io.paritytech.polkadotapp.common.utils.Urls
import io.paritytech.polkadotapp.common.utils.isDotWebMirrorHost
import io.paritytech.polkadotapp.common.utils.toCanonicalDotHost

object DotNsUtils {
    /**
     * Whether [uri] points to a .dot domain (`.dot`, or its `.dot.li` / `.paseo.li` web mirrors).
     *
     * Expects a [Uri] with a scheme (e.g. `https://coinflip.dot/path`).
     * Bare hostnames without a scheme will return `false` since [Uri.getHost] returns null for them.
     */
    fun isDotDomain(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.endsWith(".dot") || isDotWebMirrorHost(host)
    }

    /**
     * Normalize a .dot domain [Uri]:
     * - Ensures `https://` scheme
     * - Converts the `.dot.li` and `.paseo.li` web mirrors to the canonical `.dot` name
     *
     * Returns `null` if [uri] is not a .dot domain.
     */
    fun normalize(uri: Uri): Uri? {
        val withScheme = Urls.ensureHttpsProtocol(uri)

        if (!isDotDomain(withScheme)) return null

        val host = withScheme.host ?: return null

        val dotDomain = host.toCanonicalDotHost()

        return withScheme.buildUpon()
            .authority(dotDomain)
            .build()
    }

    /**
     * Classify navigation from [origin] to [destination].
     *
     * - [DotNsNavigationType.EXTERNAL] if [destination] is not a .dot domain
     * - [DotNsNavigationType.SAME_DOTNS_DOMAIN] if both resolve to the same .dot host
     * - [DotNsNavigationType.CROSS_DOTNS_DOMAIN] otherwise (different .dot hosts, or null origin)
     */
    fun classifyNavigation(origin: Uri?, destination: Uri): DotNsNavigationType {
        val normalizedDest = normalize(destination)
            ?: return DotNsNavigationType.EXTERNAL

        if (origin == null) return DotNsNavigationType.CROSS_DOTNS_DOMAIN

        val normalizedOrigin = normalize(origin)

        return if (normalizedOrigin?.host == normalizedDest.host) {
            DotNsNavigationType.SAME_DOTNS_DOMAIN
        } else {
            DotNsNavigationType.CROSS_DOTNS_DOMAIN
        }
    }
}

enum class DotNsNavigationType {
    SAME_DOTNS_DOMAIN, CROSS_DOTNS_DOMAIN, EXTERNAL
}
