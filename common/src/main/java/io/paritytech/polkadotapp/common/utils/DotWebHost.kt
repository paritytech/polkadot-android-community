package io.paritytech.polkadotapp.common.utils

val DOT_WEB_MIRROR_SUFFIXES = listOf(".dot.li", ".paseo.li")

fun isDotWebMirrorHost(host: String): Boolean =
    DOT_WEB_MIRROR_SUFFIXES.any { host.endsWith(it) }

/** Canonicalizes a `.dot.li` / `.paseo.li` web-mirror host to its `.dot` form (`coinflip.dot.li` -> `coinflip.dot`); leaves other hosts unchanged. */
fun String.toCanonicalDotHost(): String {
    val suffix = DOT_WEB_MIRROR_SUFFIXES.firstOrNull { endsWith(it) } ?: return this
    return removeSuffix(suffix) + ".dot"
}
