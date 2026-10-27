package io.paritytech.polkadotapp.feature_dotns_impl.data.ipfs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.mapError
import io.paritytech.polkadotapp.common.utils.mapErrorInstance
import io.paritytech.polkadotapp.common.utils.readNBytesCompat
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolutionError
import io.paritytech.polkadotapp.feature_dotns_impl.di.DotNsHttpClient
import io.paritytech.polkadotapp.tools_car_parser.CarParser
import io.paritytech.polkadotapp.tools_ipfs_api.Cids
import io.paritytech.polkadotapp.tools_ipfs_api.IpfsContentLookup
import io.paritytech.polkadotapp.tools_ipfs_api.getIpfsLinkFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Fetches CAR archives from IPFS gateway.
 *
 * Handles two deployment formats for backward compatibility:
 * - **Legacy**: The CID points to an uploaded CAR file. Fetching raw returns the CAR directly.
 * - **New**: The CID points to a directory. Must fetch with `?format=car` to get all files.
 *
 * The fetcher first tries a raw fetch. If the response is already a CAR file, it's used directly.
 * Otherwise, it re-fetches with `?format=car` to get the directory as a CAR archive.
 */
class RealCarFetcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:DotNsHttpClient private val httpClient: OkHttpClient,
    private val ipfsContentLookup: IpfsContentLookup
) : CarFetcher {
    override suspend fun fetchCarToFile(
        contentHash: ByteArray,
        onProgress: (downloaded: Long, total: Long?) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            gatewayUrlFor(contentHash).flatMap { baseUrl ->
                fetchToFile(baseUrl, onProgress = onProgress).flatMap { rawFile ->
                    runCatching { looksLikeCarArchive(rawFile) }.flatMap { isCarArchive ->
                        if (isCarArchive) {
                            Result.success(rawFile)
                        } else {
                            rawFile.delete()
                            fetchToFile("$baseUrl?format=car", accept = CAR_MEDIA_TYPE, onProgress = onProgress)
                        }
                    }
                }
            }
        }
    }

    private suspend fun gatewayUrlFor(contentHash: ByteArray): Result<String> {
        return Cids.castCatching(contentHash)
            .mapError { DotNsResolutionError.InvalidContentHash(it) }
            .flatMap { cid -> ipfsContentLookup.getIpfsLinkFor(cid) }
    }

    private fun fetchToFile(
        url: String,
        accept: String? = null,
        onProgress: (downloaded: Long, total: Long?) -> Unit
    ): Result<File> {
        return runCatching { httpClient.newCall(buildRequest(url, accept)).execute() }
            .flatMap { response ->
                response.use {
                    if (it.isSuccessful) {
                        runCatching { it.body.saveToTempFile(onProgress) }
                    } else {
                        Result.failure(DotNsResolutionError.GatewayRejected(it.code))
                    }
                }
            }
            .mapErrorInstance<File, IOException> { DotNsResolutionError.GatewayUnreachable(it) }
    }

    private fun buildRequest(url: String, accept: String?): Request {
        val requestBuilder = Request.Builder().url(url)
        if (accept != null) {
            requestBuilder.header("Accept", accept)
        }

        return requestBuilder.build()
    }

    private fun ResponseBody.saveToTempFile(onProgress: (downloaded: Long, total: Long?) -> Unit): File {
        val total = contentLength()
        val tempFile = File.createTempFile(TEMP_CAR_PREFIX, TEMP_CAR_SUFFIX, context.cacheDir)
        try {
            byteStream().use { input ->
                tempFile.outputStream().use { output -> input.copyReporting(output, total, onProgress) }
            }
            return tempFile
        } catch (e: Throwable) {
            tempFile.delete()
            throw e
        }
    }

    /** Copies the stream while reporting cumulative bytes, throttled to avoid per-chunk churn. */
    private inline fun InputStream.copyReporting(
        output: OutputStream,
        total: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var downloaded = 0L
        var lastReported = 0L

        while (true) {
            val read = read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            if (downloaded - lastReported >= PROGRESS_REPORT_STEP_BYTES) {
                Timber.d("On Progress: $downloaded / $total")
                onProgress(downloaded, total)
                lastReported = downloaded
            }
        }

        onProgress(downloaded, total)
    }

    private fun looksLikeCarArchive(file: File): Boolean {
        val prefix = file.inputStream().use { it.readNBytesCompat(CAR_HEADER_PROBE_BYTES) }
        return CarParser.looksLikeCarArchive(prefix)
    }

    private companion object {
        const val TEMP_CAR_PREFIX = "dotns_car"
        const val TEMP_CAR_SUFFIX = ".car"
        const val CAR_MEDIA_TYPE = "application/vnd.ipld.car"

        // The CARv1 header (varint length + CBOR roots) is well under 1 KB; this prefix is a safe over-read.
        const val CAR_HEADER_PROBE_BYTES = 64 * 1024

        // Report download progress at most once per this many bytes, to avoid per-chunk StateFlow churn.
        const val PROGRESS_REPORT_STEP_BYTES = 256 * 1024L
    }
}
