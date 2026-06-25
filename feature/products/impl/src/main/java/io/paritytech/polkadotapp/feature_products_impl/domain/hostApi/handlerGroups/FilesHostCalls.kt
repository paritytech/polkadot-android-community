package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import io.paritytech.polkadotapp.feature_products_impl.domain.jsEngine.ContainerBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Host call letting a product save a generated file (receipt PDF, sales-report CSV, SVG, log dump)
 * to the device's public Downloads folder — exposed to products as
 * `window.host.ext.files.save(name, base64, mime)`.
 *
 * Why this exists: the SPA generates these files in-page as `blob:` URLs and triggers a browser
 * `<a download>`. A normal browser handles that; an Android WebView silently drops it (no
 * DownloadListener), so nothing landed on the device. Routing the bytes through the host and writing
 * via MediaStore makes downloads work inside the WebView and inside kiosk Lock Task — no storage
 * permission needed (minSdk 29+).
 *
 * Mirrors the printer / NFC host extensions.
 */
class FilesHostCalls(
    private val context: Context,
) : HostCallHandlerGroup {
    override fun registerOn(bridge: ContainerBridge) {
        bridge.registerHandler<FileSaveDto, FileSaveResultDto>("filesSave") { params ->
            runCatching {
                val savedName = withContext(Dispatchers.IO) {
                    writeToDownloads(params.name, params.mime, params.base64)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Downloads: $savedName", Toast.LENGTH_LONG).show()
                }
                FileSaveResultDto(savedAs = savedName)
            }
        }
    }

    /** Writes [base64] bytes to the public Downloads collection. Returns the saved display name. */
    private fun writeToDownloads(name: String, mime: String, base64: String): String {
        val safeName = name.ifBlank { "download" }
        val safeMime = mime.ifBlank { "application/octet-stream" }
        // Tolerate an accidental data: URL prefix ("data:...;base64,<payload>").
        val payload = base64.substringAfterLast("base64,", base64)
        val bytes = Base64.decode(payload, Base64.DEFAULT)

        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, safeMime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, pending)
            ?: throw IOException("Could not create a Downloads entry for $safeName")

        try {
            resolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                ?: throw IOException("Could not open output stream for $safeName")
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return safeName
    }
}

private data class FileSaveDto(
    val name: String,
    val base64: String,
    val mime: String,
)

private data class FileSaveResultDto(val savedAs: String)
