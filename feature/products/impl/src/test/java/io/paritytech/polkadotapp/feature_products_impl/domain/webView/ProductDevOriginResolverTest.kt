package io.paritytech.polkadotapp.feature_products_impl.domain.webView

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class ProductDevOriginResolverTest {
    private val origins = mutableMapOf<ProductId, String>()
    private val debugAppOrigins = object : DebugAppOrigins {
        override fun get(productId: ProductId): String? = origins[productId]

        override fun set(productId: ProductId, origin: String?) {
            if (origin == null) origins.remove(productId) else origins[productId] = origin
        }
    }
    private val dotNsTldProvider: DotNsTldProvider = mock<DotNsTldProvider>().also {
        runBlocking { whenever(it.getTld()).thenReturn(Result.success(DotNsTld.parse("dot")!!)) }
    }
    private val resolver = ProductDevOriginResolver(debugAppOrigins, dotNsTldProvider)

    private val coinflip = ProductId.fromStoredValue("coinflip.dot")

    @Test
    fun `a product with a dev origin is served from it`() = runBlocking {
        debugAppOrigins.set(coinflip, "http://127.0.0.1:5183")

        assertEquals("http://127.0.0.1:5183", resolver.devOriginFor("coinflip.dot"))
    }

    @Test
    fun `a product without a dev origin keeps its archive`() = runBlocking {
        assertNull(resolver.devOriginFor("coinflip.dot"))
    }

    @Test
    fun `only the base host is overridden`() = runBlocking {
        // The worker and the app subname keep their published archives even while the SPA is local.
        debugAppOrigins.set(coinflip, "http://127.0.0.1:5183")

        assertNull(resolver.devOriginFor("worker.coinflip.dot"))
        assertNull(resolver.devOriginFor("app.coinflip.dot"))
    }

    @Test
    fun `a web-mirror host maps to the canonical product`() = runBlocking {
        debugAppOrigins.set(coinflip, "http://127.0.0.1:5183")

        assertEquals("http://127.0.0.1:5183", resolver.devOriginFor("coinflip.dot.li"))
    }

    @Test
    fun `a host that is not a product is left alone`() = runBlocking {
        assertNull(resolver.devOriginFor("example.com"))
    }
}
