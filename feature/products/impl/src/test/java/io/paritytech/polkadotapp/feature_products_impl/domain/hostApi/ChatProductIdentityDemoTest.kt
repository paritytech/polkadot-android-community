package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatProductIdentityDemoTest {
    private val tld = DotNsTld.parse("paseo")!!
    private val chat = ProductId.fromStoredValue("chat.paseo")

    @Test
    fun `the source product and its app executable call the host as chat of the active tld`() {
        assertEquals(chat, productId("chat-spa-demo.paseo").withChatIdentityDemo(tld, source = "chat-spa-demo.paseo"))
        assertEquals(chat, productId("app.chat-spa-demo.paseo").withChatIdentityDemo(tld, source = "chat-spa-demo.paseo"))
    }

    @Test
    fun `other products keep their own id`() {
        val browse = productId("browse.paseo")

        assertEquals(browse, browse.withChatIdentityDemo(tld, source = "chat-spa-demo.paseo"))
    }

    @Test
    fun `no source or a source outside the active tld maps nothing`() {
        val demo = productId("chat-spa-demo.paseo")

        assertEquals(demo, demo.withChatIdentityDemo(tld, source = ""))
        assertEquals(demo, demo.withChatIdentityDemo(tld, source = "chat-spa-demo.dot"))
    }

    private fun productId(value: String): ProductId = ProductId.fromString(value, tld).getOrThrow()
}
