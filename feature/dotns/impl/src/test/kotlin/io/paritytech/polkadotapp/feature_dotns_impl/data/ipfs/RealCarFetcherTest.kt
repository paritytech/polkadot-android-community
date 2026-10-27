package io.paritytech.polkadotapp.feature_dotns_impl.data.ipfs

import android.content.Context
import io.novasama.substrate_sdk_android.extensions.fromHex
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolutionError
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.assertFailureOf
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_ipfs_api.IpfsContentLookup
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.IOException
import java.net.UnknownHostException

class RealCarFetcherTest {
    private val context: Context = mock()
    private val ipfsContentLookup: IpfsContentLookup = mock()

    @Test
    fun `a content hash that is not a cid fails as invalid`() = runBlocking<Unit> {
        val result = fetcher(gatewayAnswering(200)).fetchCarToFile(byteArrayOf(0x00), onProgress = { _, _ -> })

        assertFailureOf<DotNsResolutionError.InvalidContentHash>(result)
    }

    @Test
    fun `an http error status fails as gateway rejected`() = runBlocking<Unit> {
        withGatewayAddress()

        val result = fetcher(gatewayAnswering(404)).fetchCarToFile(CID, onProgress = { _, _ -> })

        assertFailureOf<DotNsResolutionError.GatewayRejected>(result)
        assertEquals(404, (result.exceptionOrNull() as DotNsResolutionError.GatewayRejected).statusCode)
    }

    @Test
    fun `a network failure fails as gateway unreachable`() = runBlocking<Unit> {
        withGatewayAddress()

        val result = fetcher(gatewayFailing(UnknownHostException(GATEWAY_HOST))).fetchCarToFile(CID, onProgress = { _, _ -> })

        assertFailureOf<DotNsResolutionError.GatewayUnreachable>(result)
    }

    @Test
    fun `a gateway address that cannot be read is not reported as a transport failure`() = runBlocking<Unit> {
        val lookupFailure = IOException("remote config unavailable")
        withGatewayAddressFailing(lookupFailure)

        val result = fetcher(gatewayAnswering(200)).fetchCarToFile(CID, onProgress = { _, _ -> })

        assertSame(lookupFailure, result.exceptionOrNull())
    }

    @Test
    fun `a gateway address that is not a url fails instead of throwing`() = runBlocking<Unit> {
        withGatewayAddress(MALFORMED_GATEWAY_LINK)

        val result = fetcher(gatewayAnswering(200)).fetchCarToFile(CID, onProgress = { _, _ -> })

        assertFailureOf<IllegalArgumentException>(result)
    }

    @Test
    fun `a directory is fetched again as a car archive`() = runBlocking<Unit> {
        withGatewayAddress()
        val requests = mutableListOf<Request>()

        val result = fetcher(gatewayServingDirectory(carExportCode = 200, requests)).fetchCarToFile(CID, onProgress = { _, _ -> })

        val file = result.getOrNull()
        assertNotNull("expected the exported archive, got ${result.exceptionOrNull()}", file)
        assertEquals(CAR_EXPORT, file!!.readText())
        file.delete()
        assertEquals(CAR_MEDIA_TYPE, requests.last().header("Accept"))
        assertEquals("car", requests.last().url.queryParameter("format"))
    }

    @Test
    fun `a rejected car export fails as gateway rejected`() = runBlocking<Unit> {
        withGatewayAddress()

        val result = fetcher(gatewayServingDirectory(carExportCode = 406, mutableListOf())).fetchCarToFile(CID, onProgress = { _, _ -> })

        assertFailureOf<DotNsResolutionError.GatewayRejected>(result)
        assertEquals(406, (result.exceptionOrNull() as DotNsResolutionError.GatewayRejected).statusCode)
    }

    private suspend fun withGatewayAddress(link: String = "https://$GATEWAY_HOST/ipfs/cid") {
        whenever(ipfsContentLookup.getIpfsLinkFor(any<String>())).thenReturn(Result.success(link))
    }

    private suspend fun withGatewayAddressFailing(error: Throwable) {
        whenever(ipfsContentLookup.getIpfsLinkFor(any<String>())).thenReturn(Result.failure(error))
    }

    private fun fetcher(httpClient: OkHttpClient) = RealCarFetcher(
        context = context,
        httpClient = httpClient,
        ipfsContentLookup = ipfsContentLookup,
    )

    private fun gatewayAnswering(code: Int) = gatewayWith { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(ByteArray(0).toResponseBody())
            .build()
    }

    private fun gatewayServingDirectory(carExportCode: Int, requests: MutableList<Request>) = gatewayWith { chain ->
        val request = chain.request()
        requests += request
        val isCarExport = request.url.queryParameter("format") != null
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (isCarExport) carExportCode else 200)
            .message("HTTP")
            .body((if (isCarExport) CAR_EXPORT else DIRECTORY_LISTING).toResponseBody())
            .build()
    }

    private fun gatewayFailing(error: IOException) = gatewayWith { throw error }

    private fun gatewayWith(interceptor: Interceptor) = OkHttpClient.Builder().addInterceptor(interceptor).build()

    private companion object {
        const val GATEWAY_HOST = "gateway.invalid"
        const val MALFORMED_GATEWAY_LINK = "/cid"
        const val CAR_MEDIA_TYPE = "application/vnd.ipld.car"
        const val DIRECTORY_LISTING = "<html>index of /ipfs/cid</html>"
        const val CAR_EXPORT = "car archive bytes"
        val CID = "01701220".fromHex() + ByteArray(32) { 3 }
    }
}
