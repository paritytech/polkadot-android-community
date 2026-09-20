package io.paritytech.polkadotapp.common.utils

import java.net.URI

/**
 * A product served from a development server on the machine instead of from dotNS.
 *
 * The device reaches that server over its own loopback: `adb reverse tcp:<port> tcp:<port>` maps
 * `localhost` on the device onto the developer machine, so the address the product is served from is
 * a local one. `10.0.2.2` is the emulator's alias for its host.
 */
object LocalDevHost {
    private val HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /**
     * Canonical `http://<host>[:<port>]` origin of [url], or null when [url] does not name a local
     * development server. A missing scheme reads as http; https is rejected, since a dev server is
     * served in the clear.
     */
    fun parseOrigin(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith(Urls.HTTPS_PREFIX, ignoreCase = true) -> return null
            trimmed.startsWith(Urls.HTTP_PREFIX, ignoreCase = true) -> trimmed
            else -> Urls.HTTP_PREFIX + trimmed
        }

        val parsed = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase() ?: return null
        if (host !in HOSTS) return null

        val port = parsed.port.takeIf { it != -1 }?.let { ":$it" }.orEmpty()

        return "${Urls.HTTP_PREFIX}$host$port"
    }
}
