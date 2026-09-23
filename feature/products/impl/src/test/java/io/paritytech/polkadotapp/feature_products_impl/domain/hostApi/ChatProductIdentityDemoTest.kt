package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatProductIdentityDemoTest {
    private val tld = DotNsTld.parse("paseo")!!

    @Test
    fun `the source product calls the host as chat of the active tld`() {
        val demo = ChatProductIdentityDemo(sourceProductId = "chat-spa-demo.paseo")

        assertEquals(ProductId.fromStoredValue("chat.paseo"), demo.callingProductId(productId("chat-spa-demo.paseo"), tld))
    }

    @Test
    fun `the app executable of the source product is mapped too`() {
        val demo = ChatProductIdentityDemo(sourceProductId = "chat-spa-demo.paseo")

        assertEquals(ProductId.fromStoredValue("chat.paseo"), demo.callingProductId(productId("app.chat-spa-demo.paseo"), tld))
    }

    @Test
    fun `other products keep their own id`() {
        val demo = ChatProductIdentityDemo(sourceProductId = "chat-spa-demo.paseo")

        assertEquals(productId("browse.paseo"), demo.callingProductId(productId("browse.paseo"), tld))
    }

    @Test
    fun `disabled demo maps nothing`() {
        val demo = ChatProductIdentityDemo(sourceProductId = null)

        assertEquals(productId("chat-spa-demo.paseo"), demo.callingProductId(productId("chat-spa-demo.paseo"), tld))
    }

    @Test
    fun `a source outside the active tld maps nothing`() {
        val demo = ChatProductIdentityDemo(sourceProductId = "chat-spa-demo.dot")

        assertEquals(productId("chat-spa-demo.paseo"), demo.callingProductId(productId("chat-spa-demo.paseo"), tld))
    }

    private fun productId(value: String): ProductId = ProductId.fromString(value, tld).getOrThrow()
}
