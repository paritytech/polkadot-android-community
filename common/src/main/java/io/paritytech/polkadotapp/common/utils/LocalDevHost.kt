package io.paritytech.polkadotapp.common.utils

import java.net.URI

/**
 * A product served from a development server on the developer's own network instead of from dotNS.
 *
 * Two ways to reach it. Over USB, `adb reverse tcp:<port> tcp:<port>` maps `localhost` on the device
 * onto the developer machine (`10.0.2.2` is the emulator's equivalent). Over Wi-Fi, the machine's own
 * private address serves every device on the same network, which is what more than one device needs.
 *
 * Private ranges only — a public address is never a development server, and treating one as such would
 * hand the host API to whoever answers at it.
 */
object LocalDevHost {
    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /**
     * Canonical `http://<host>[:<port>]` origin of [url], or null when [url] does not name a local
     * development server — a loopback host or a private-range IPv4 address. A missing scheme reads as
     * http; https is rejected, since a dev server is served in the clear.
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
        if (host !in LOOPBACK_HOSTS && !host.isPrivateIpv4()) return null

        val port = parsed.port.takeIf { it != -1 }?.let { ":$it" }.orEmpty()

        return "${Urls.HTTP_PREFIX}$host$port"
    }

    private fun String.isPrivateIpv4(): Boolean {
        val parts = split('.')
        if (parts.size != 4) return false

        val octets = parts.mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false

        val (first, second) = octets

        return when (first) {
            10 -> true
            172 -> second in 16..31
            192 -> second == 168
            else -> false
        }
    }
}
