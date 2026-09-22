package io.paritytech.polkadotapp.feature_statement_store_impl.data

import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealStatementStorePeerTest {
    private val peer = RealStatementStorePeer(KnownChains(people = "people", assetHub = "hub", bulletIn = "bulletin", hydration = null))

    @Test
    fun `a subscription nobody collects answers nothing`() = runBlocking<Unit> {
        peer.track(flow<Result<String>> { })

        assertFalse(answered())
    }

    @Test
    fun `an open subscription that has sent nothing is not answered`() = runBlocking<Unit> {
        val subscription = Subscription()

        subscription.open {
            assertFalse(answered())
        }
    }

    @Test
    fun `a page from the peer answers the subscription`() = runBlocking<Unit> {
        val subscription = Subscription()

        subscription.open {
            subscription.sendPage()

            assertTrue(answered())
        }
    }

    @Test
    fun `a failed page does not answer the subscription`() = runBlocking<Unit> {
        val subscription = Subscription()

        subscription.open {
            subscription.fail()

            assertFalse(answered())
        }
    }

    @Test
    fun `closing the last answered subscription leaves nothing answered`() = runBlocking<Unit> {
        val subscription = Subscription()

        subscription.open { subscription.sendPage() }

        assertFalse(answered())
    }

    @Test
    fun `one answered subscription is enough while another is still waiting`() = runBlocking<Unit> {
        val answering = Subscription()
        val quiet = Subscription()

        answering.open {
            quiet.open {
                answering.sendPage()

                assertTrue(answered())
            }

            assertTrue(answered())
        }

        assertFalse(answered())
    }

    private suspend fun answered(): Boolean = peer.observeAnswered().first()

    /** One tracked subscription, driven page by page so every assertion runs after the page landed. */
    private inner class Subscription {
        private val pages = MutableSharedFlow<Result<String>>()
        private var delivered = CompletableDeferred<Unit>()

        suspend fun open(assertions: suspend () -> Unit) = coroutineScope {
            val collection = launch { peer.track(pages).collect { delivered.complete(Unit) } }
            pages.subscriptionCount.first { it > 0 }

            try {
                assertions()
            } finally {
                close(collection)
            }
        }

        suspend fun sendPage() = deliver(Result.success("page"))

        suspend fun fail() = deliver(Result.failure(IllegalStateException("unreadable page")))

        private suspend fun deliver(page: Result<String>) {
            delivered = CompletableDeferred()
            pages.emit(page)
            delivered.await()
        }

        private suspend fun close(collection: Job) {
            collection.cancel()
            collection.join()
        }
    }
}
