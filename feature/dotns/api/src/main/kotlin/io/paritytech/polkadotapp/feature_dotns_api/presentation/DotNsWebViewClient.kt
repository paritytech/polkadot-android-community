package io.paritytech.polkadotapp.feature_dotns_api.presentation

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.paritytech.polkadotapp.common.utils.notFoundResponse
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsUtils
import io.paritytech.polkadotapp.feature_dotns_api.domain.resolveToLocalFile
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File

open class DotNsWebViewClient(
    private val dotNsResolver: DotNsResolver
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url

        Timber.d("Intercepting request for $url")

        // Intercept http(s)://*.dot requests and serve from local content
        if (!DotNsUtils.isDotDomain(url)) {
            Timber.d("Not dotNs domain: $url")

            return null
        }

        val requestPath = (url.path ?: "/").removePrefix("/")

        val resolveHostFile = resolveHostFile(url) ?: run {
            Timber.w("Archive root not resolved $url")
            return notFoundResponse()
        }
        val resolvedFile = resolveFile(resolveHostFile, requestPath) ?: run {
            Timber.w("File within archive not resolved $url")
            return notFoundResponse()
        }

        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(resolvedFile.extension)
            ?: "application/octet-stream"

        Timber.d("Successfully resolved file for $url: ${resolvedFile.name}, mimeType=$mimeType")

        return WebResourceResponse(mimeType, "UTF-8", resolvedFile.inputStream())
    }

    fun resolveHostFile(uri: Uri?): File? {
        val host = uri?.host
        if (host == null) return null

        return runBlocking {
            dotNsResolver.resolveToLocalFile(host)
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

    private fun notFoundResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream("Not Found".toByteArray())
        ).apply {
            setStatusCodeAndReasonPhrase(404, "Not Found")
        }
    }
}
