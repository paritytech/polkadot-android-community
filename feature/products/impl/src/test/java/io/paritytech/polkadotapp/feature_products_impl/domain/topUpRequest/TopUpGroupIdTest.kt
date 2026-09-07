package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The top-up written into the id of its coinage group.
 *
 * Nothing else records a top-up, so this is the only thing standing between a relaunch and an operation the
 * host can no longer describe. What it has to survive is a product naming its top-ups anything it likes.
 */
@OptIn(ExperimentalTime::class)
class TopUpGroupIdTest {
    @Test
    fun `an operation survives the round trip through its group id`() {
        val operation = operation(PaymentTopUpId("topup-1"))

        assertEquals(operation, operation.groupId().asTopUpOperation())
    }

    /**
     * A product's id is an arbitrary string, and the separator is the one character that would let it be
     * read as something else. Hex-encoding it is what makes "a:b" and "a" plus "b" different top-ups.
     */
    @Test
    fun `an id containing the separator survives the round trip`() {
        val operation = operation(PaymentTopUpId("a:1000:9999:b"))

        assertEquals(operation, operation.groupId().asTopUpOperation())
    }

    @Test
    fun `an empty id survives the round trip`() {
        val operation = operation(PaymentTopUpId(""))

        assertEquals(operation, operation.groupId().asTopUpOperation())
    }

    /** Two top-ups of one product are told apart by the id alone, whatever else about them matches. */
    @Test
    fun `different ids of one product name different groups`() {
        assertTrue(operation(PaymentTopUpId("one")).groupId() != operation(PaymentTopUpId("two")).groupId())
    }

    /** The prefix has to name the top-up without knowing when it opened or what it was for. */
    @Test
    fun `the prefix of an operation matches its own group id`() {
        val operation = operation(PaymentTopUpId("topup-1"))

        assertTrue(operation.groupId().value.startsWith(topUpGroupPrefixOf(operation.productId, operation.id)))
    }

    @Test
    fun `the prefix of one top-up does not match another's group id`() {
        val other = operation(PaymentTopUpId("topup-2"))

        assertTrue(!other.groupId().value.startsWith(topUpGroupPrefixOf(PRODUCT, PaymentTopUpId("topup-1"))))
    }

    @Test
    fun `a group this app did not write is not read as a top-up`() {
        assertNull(CoinageOperationGroupId("chat-claim:42").asTopUpOperation())
    }

    @Test
    fun `a top-up group with an unreadable body is not read as a top-up`() {
        assertNull(CoinageOperationGroupId("top-up:alice.dot:6e:not-a-number:100").asTopUpOperation())
    }

    private fun operation(id: PaymentTopUpId) = TopUpOperation(
        id = id,
        productId = PRODUCT,
        amount = 100.intoBalance(),
        startedAt = OPENED,
    )

    private companion object {
        val DOT_TLD: DotNsTld = requireNotNull(DotNsTld.parse("dot"))

        val PRODUCT: ProductId = ProductId.fromString("alice.dot", DOT_TLD).getOrThrow()
        val OPENED: Instant = Instant.fromEpochSeconds(1_000_000)
    }
}
