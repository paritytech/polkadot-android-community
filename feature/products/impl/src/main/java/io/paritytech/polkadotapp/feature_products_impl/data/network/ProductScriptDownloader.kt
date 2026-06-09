package io.paritytech.polkadotapp.feature_products_impl.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface ProductScriptDownloader {
    suspend fun download(url: String): Result<String>
}

@Singleton
class HttpProductScriptDownloader @Inject constructor(
    private val httpClient: OkHttpClient,
) : ProductScriptDownloader {
    override suspend fun download(url: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to download script: HTTP ${response.code}")
                }

                response.body.string()
            }
        }
    }
}
