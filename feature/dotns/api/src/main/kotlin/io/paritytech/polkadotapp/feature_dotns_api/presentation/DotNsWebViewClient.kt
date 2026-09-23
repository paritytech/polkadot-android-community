package io.paritytech.polkadotapp.feature_dotns_api.presentation

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.paritytech.polkadotapp.common.utils.notFoundResponse
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsUtils
import io.paritytech.polkadotapp.feature_dotns_api.domain.getTldRetrying
import io.paritytech.polkadotapp.feature_dotns_api.domain.resolveToLocalFile
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.Locale

open class DotNsWebViewClient(
    private val dotNsResolver: DotNsResolver,
    private val dotNsTldProvider: DotNsTldProvider,
    private val servingHostResolver: DotNsServingHostResolver = DotNsServingHostResolver.Identity,
    // Stamped onto the main-frame document response so the caller can enforce document-level policy
    // (e.g. a Content-Security-Policy that forbids iframes) engine-side rather than by heuristics.
    private val mainDocumentResponseHeaders: Map<String, String> = emptyMap(),
    // Debug builds only: when a dev origin is configured for the requested host, the bytes come from
    // the developer's machine instead of the archive. It is applied here, in interception, and never
    // to the loaded URL: the WebView keeps the product's real origin (`https://<productId>`), so
    // product identity, permissions and the TrUAPI bridge are derived exactly as in a release build.
    private val devOriginResolver: DotNsDevOriginResolver = DotNsDevOriginResolver.None,
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url

        Timber.d("Intercepting request for $url")

        val tld = runBlocking { dotNsTldProvider.getTldRetrying() }
        if (!DotNsUtils.isDotDomain(url, tld)) {
            Timber.d("Not dotNs domain: $url")

            return null
        }

        val devOrigin = runBlocking { devOriginResolver.devOriginFor(url.host.orEmpty()) }
        if (devOrigin != null) {
            // The resolver is never asked for this domain, so the host's load progress has to be
            // told by hand that there is a document to show.
            if (request.isForMainFrame) {
                (dotNsResolver as? DotNsContentLoader)?.markServedFromDevOrigin(url.host.orEmpty())
            }
            return proxyToDevOrigin(devOrigin, url, request)
        }

        val requestPath = (url.path ?: "/").removePrefix("/")

        val resolveHostFile = resolveHostFile(url) ?: run {
            Timber.w("Archive root not resolved $url")
            return notFoundResponse()
        }
        val resolvedFile = resolveFile(resolveHostFile, requestPath)
            ?: spaFallbackFile(resolveHostFile, request)
            ?: run {
                Timber.w("File within archive not resolved $url")
                return notFoundResponse()
            }

        val mimeType = resolveMimeType(resolvedFile)

        Timber.d("Successfully resolved file for $url: ${resolvedFile.name}, mimeType=$mimeType")

        val stream = resolvedFile.inputStream()
        return if (request.isForMainFrame && mainDocumentResponseHeaders.isNotEmpty()) {
            WebResourceResponse(mimeType, "UTF-8", 200, "OK", mainDocumentResponseHeaders, stream)
        } else {
            WebResourceResponse(mimeType, "UTF-8", stream)
        }
    }

    fun resolveHostFile(uri: Uri?): File? {
        val host = uri?.host ?: return null

        return runBlocking {
            val servedHost = servingHostResolver.servingHostFor(host)
            dotNsResolver.resolveToLocalFile(servedHost)
                .getOrNull() // TODO: Bad ux. We have to ask user to reload the page
        }
    }

    /**
     * Resolves a request path to a file in the content directory.
     * 1. If contentDir is a plain file (single-file archive), serve it directly
     * 2. Try the exact path
     * 3. If no extension, try path/index.html (directory index)
     * 4. For root requests, try index.html
     */
    fun resolveFile(contentDir: File?, requestPath: String): File? {
        // Single-file archive — contentDir is a file, not a directory
        if (contentDir?.isFile == true) return contentDir

        // Root request, but it is a folder - try index.html
        if (requestPath.isEmpty()) {
            val index = File(contentDir, "index.html")
            if (index.exists()) return index
        }

        val exact = File(contentDir, requestPath)
        if (exact.exists() && exact.isFile) return exact

        // If no extension, try as directory with index.html
        if (!requestPath.contains('.')) {
            val dirIndex = File(contentDir, "$requestPath/index.html")
            if (dirIndex.exists()) return dirIndex
        }

        return null
    }

    /**
     * SPA fallback. Client-side routes (e.g. `/apps`) have no matching file in the archive — they
     * exist only in the app's JS router. When the WebView issues a hard main-frame request for such
     * a route (back across a cross-document boundary, refresh, or a shared deep link), serve the
     * archive's root `index.html` so the SPA boots and routes itself from `window.location`, mirroring
     * a web server rewriting unknown routes to `index.html`. Sub-resource requests still 404 so that
     * genuinely missing assets are not masked.
     */
    private fun spaFallbackFile(contentDir: File?, request: WebResourceRequest): File? {
        if (!request.isForMainFrame) return null
        return File(contentDir, "index.html").takeIf { it.exists() && it.isFile }
    }

    private fun resolveMimeType(file: File): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            ?: guessMimeFromContent(file)
            ?: "application/octet-stream"
    }

    private fun guessMimeFromContent(file: File): String? =
        file.inputStream().buffered().use { URLConnection.guessContentTypeFromStream(it) }

    /**
     * Serves the request from a developer's local server instead of the archive. Only the bytes move:
     * the response is handed back through interception, so the document keeps the archive's origin and
     * everything keyed off it (identity, permissions, storage) is unchanged.
     */
    private fun proxyToDevOrigin(
        origin: String,
        url: Uri,
        request: WebResourceRequest,
    ): WebResourceResponse {
        val method = (request.method ?: "GET").uppercase(Locale.ROOT)
        if (method != "GET" && method != "HEAD") {
            Timber.w("Dev origin: refusing to proxy $method $url, only GET/HEAD are supported")
            return notFoundResponse()
        }

        val base = origin.trimEnd('/')
        val target = base + (url.encodedPath ?: "/") + (url.encodedQuery?.let { "?$it" } ?: "")

        return try {
            var connection = openDevOrigin(target, method, request)
            var status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND && request.isForMainFrame) {
                // Same rationale as [spaFallbackFile]: a client-side route is no more a file on the dev
                // server than in the archive, so boot the SPA from its root document and let it route.
                connection.disconnect()
                connection = openDevOrigin("$base/index.html", method, request)
                status = connection.responseCode
            }
            devOriginResponse(connection, url, status, request)
        } catch (e: IOException) {
            Timber.w(e, "dev origin unreachable for $url")
            WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream("dev origin unreachable: $target".toByteArray())
            ).apply {
                setStatusCodeAndReasonPhrase(502, "Bad Gateway")
            }
        }
    }

    private fun openDevOrigin(
        target: String,
        method: String,
        request: WebResourceRequest,
    ): HttpURLConnection {
        Timber.d("Dev origin: $method $target")

        return (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = DEV_ORIGIN_CONNECT_TIMEOUT_MS
            readTimeout = DEV_ORIGIN_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            // The stream is passed to the WebView verbatim and Content-Encoding is dropped, so ask for
            // unencoded bytes rather than unwrapping gzip here.
            setRequestProperty("Accept-Encoding", "identity")
            request.requestHeaders?.get("Accept")?.let { setRequestProperty("Accept", it) }
        }
    }

    private fun devOriginResponse(
        connection: HttpURLConnection,
        url: Uri,
        status: Int,
        request: WebResourceRequest,
    ): WebResourceResponse {
        val contentType = connection.contentType
        val mimeType = contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(devOriginExtension(url))
            ?: "application/octet-stream"
        val encoding = contentType?.let { charsetOf(it) } ?: "UTF-8"
        val headers = buildMap {
            // The next edit must be one reload away, so nothing from the laptop is ever cached.
            put("Cache-Control", "no-store")
            if (request.isForMainFrame) putAll(mainDocumentResponseHeaders)
        }
        // An upstream error is forwarded as-is so a genuinely missing asset still reads as missing.
        val stream = connection.devOriginStream(status)
        val reasonPhrase = (connection.responseMessage ?: "").ifBlank { "OK" }

        Timber.d("Dev origin: $status $url mimeType=$mimeType")

        return WebResourceResponse(mimeType, encoding, status, reasonPhrase, headers, stream)
    }

    private fun HttpURLConnection.devOriginStream(status: Int): InputStream =
        runCatching {
            if (status >= HttpURLConnection.HTTP_BAD_REQUEST) errorStream ?: inputStream else inputStream
        }.getOrElse { ByteArrayInputStream(ByteArray(0)) }

    private fun devOriginExtension(url: Uri): String =
        url.encodedPath?.substringAfterLast('/')?.substringAfterLast('.', "").orEmpty()

    private fun charsetOf(contentType: String): String? =
        contentType.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim('"', ' ')
            ?.takeIf { it.isNotEmpty() }

    private fun notFoundResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream("Not Found".toByteArray())
        ).apply {
            setStatusCodeAndReasonPhrase(404, "Not Found")
        }
    }

    private companion object {
        const val DEV_ORIGIN_CONNECT_TIMEOUT_MS = 5_000
        const val DEV_ORIGIN_READ_TIMEOUT_MS = 15_000
    }
}
