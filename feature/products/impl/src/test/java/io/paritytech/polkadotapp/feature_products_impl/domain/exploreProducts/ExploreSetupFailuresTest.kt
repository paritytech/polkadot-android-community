package io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolutionError
import io.paritytech.polkadotapp.feature_products_api.domain.error.ProductResolutionError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class ExploreSetupFailuresTest {
    @Test
    fun `a name with no readable content is not found, as iOS contentHashNotFound, decode nil and resolutionFailed`() {
        assertContentFailure(ExploreSetupFailure.NOT_FOUND, DotNsResolutionError.NotFound(DOMAIN))
    }

    @Test
    fun `a failed registry read is unreachable, as iOS contractCallFailed`() {
        assertContentFailure(ExploreSetupFailure.UNREACHABLE, DotNsResolutionError.RegistryUnreachable(IOException()))
    }

    @Test
    fun `an unsupported content hash is damaged, as iOS unsupportedEip1577Prefix`() {
        assertContentFailure(ExploreSetupFailure.DAMAGED, DotNsResolutionError.InvalidContentHash(IllegalArgumentException()))
    }

    @Test
    fun `a content hash that is not a cid is damaged, as iOS CarFetcherError invalidContentHash`() {
        assertContentFailure(ExploreSetupFailure.DAMAGED, DotNsResolutionError.InvalidContentHash(IllegalStateException()))
    }

    @Test
    fun `a gateway address that cannot be read is a plain error, with no iOS step`() {
        assertContentFailure(ExploreSetupFailure.UNKNOWN, IOException("remote config unavailable"))
    }

    @Test
    fun `an http error status from the gateway is a plain error, as iOS HTTPResponseError`() {
        assertContentFailure(ExploreSetupFailure.UNKNOWN, DotNsResolutionError.GatewayRejected(404))
    }

    @Test
    fun `a transport failure during the download is a url error, as iOS isConnectionError`() {
        assertContentFailure(ExploreSetupFailure.CONNECTION, DotNsResolutionError.GatewayUnreachable(UnknownHostException()))
    }

    @Test
    fun `an archive that cannot be unpacked or stored is damaged, as iOS carParseFailed and storageFailed`() {
        assertContentFailure(ExploreSetupFailure.DAMAGED, DotNsResolutionError.ContentDamaged(IOException()))
    }

    @Test
    fun `an untyped content failure is a plain error, as the iOS presenter default`() {
        assertContentFailure(ExploreSetupFailure.UNKNOWN, IllegalStateException())
    }

    @Test
    fun `a malformed product manifest is damaged, as iOS malformedManifest`() {
        assertEquals(ExploreSetupFailure.DAMAGED, classifyProductFailure(ProductResolutionError.MalformedManifest))
    }

    @Test
    fun `any other product failure is a plain error`() {
        assertEquals(ExploreSetupFailure.UNKNOWN, classifyProductFailure(ProductResolutionError.Unknown))
    }

    private fun assertContentFailure(expected: ExploreSetupFailure, error: Throwable) {
        assertEquals(expected, classifyContentFailure(error))
    }

    private companion object {
        const val DOMAIN = "browse.paseo"
    }
}
