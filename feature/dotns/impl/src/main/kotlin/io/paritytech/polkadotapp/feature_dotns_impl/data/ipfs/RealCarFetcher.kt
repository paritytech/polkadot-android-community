package io.paritytech.polkadotapp.feature_dotns_impl.data.ipfs

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.tools_car_parser.CarParser
import io.paritytech.polkadotapp.tools_ipfs_api.Cids
import io.paritytech.polkadotapp.tools_ipfs_api.IpfsContentLookup
import io.paritytech.polkadotapp.tools_ipfs_api.getIpfsLinkFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
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
    private val httpClient: OkHttpClient,
    private val ipfsContentLookup: IpfsContentLookup
) : CarFetcher {
    override suspend fun fetchCar(contentHash: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            Cids.castCatching(contentHash)
                .flatMap { cid -> ipfsContentLookup.getIpfsLinkFor(cid) }
                .mapCatching { baseUrl ->
                    // Phase 1: Fetch raw (no format override)
                    val rawBytes = fetch(baseUrl)

                    if (CarParser.looksLikeCarArchive(rawBytes)) {
                        // Legacy: CID is an uploaded CAR file — use directly
                        rawBytes
                    } else {
                        // New: CID is a directory — re-fetch with ?format=car
                        fetch("$baseUrl?format=car", accept = "application/vnd.ipld.car")
                    }
                }
        }
    }

    private fun fetch(url: String, accept: String? = null): ByteArray {
        val requestBuilder = Request.Builder().url(url)
        if (accept != null) {
            requestBuilder.header("Accept", accept)
        }

        return httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("IPFS fetch failed: HTTP ${response.code} for $url")
            }
            response.body.bytes()
        }
    }
}
