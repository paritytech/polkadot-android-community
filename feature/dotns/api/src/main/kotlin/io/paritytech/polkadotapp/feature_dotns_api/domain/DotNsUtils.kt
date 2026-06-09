package io.paritytech.polkadotapp.feature_dotns_api.domain

import android.R.attr.host
import android.net.Uri
import io.paritytech.polkadotapp.common.utils.Urls

object DotNsUtils {
    private val DOT_LI_SUFFIX = Regex("\\.dot\\.li$")

    /**
     * Whether [uri] points to a .dot domain (`.dot` or `.dot.li`).
     *
     * Expects a [Uri] with a scheme (e.g. `https://coinflip.dot/path`).
     * Bare hostnames without a scheme will return `false` since [Uri.getHost] returns null for them.
     */
    fun isDotDomain(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.endsWith(".dot") || host.endsWith(".dot.li")
    }

    /**
     * Normalize a .dot domain [Uri]:
     * - Ensures `https://` scheme
     * - Converts `.dot.li` to `.dot`
     *
     * Returns `null` if [uri] is not a .dot domain.
     */
    fun normalize(uri: Uri): Uri? {
        val withScheme = Urls.ensureHttpsProtocol(uri)

        if (!isDotDomain(withScheme)) return null

        val host = withScheme.host ?: return null

        val dotDomain = host.replace(DOT_LI_SUFFIX, ".dot")

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
