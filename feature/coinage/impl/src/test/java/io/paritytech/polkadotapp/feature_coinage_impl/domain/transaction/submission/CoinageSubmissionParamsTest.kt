package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Pins the persisted encoding of the coinage submission policies' parameters.
 *
 * These bytes are stored with every scheduled transaction, so the hex strings below must never be edited. A
 * change that makes the conformance tests fail corrupts rows already written: freeze the old shape as a
 * versioned class, decode both, and add a new test beside these.
 */
@OptIn(ExperimentalTime::class)
class CoinageSubmissionParamsTest {
    @Test
    fun `a transfer with a retry window survives a round trip`() {
        val params = TransferSubmissionParams(retryUntil = RETRY_UNTIL)

        val policy = CoinageSubmissionParams.splitPolicy(params)

        assertEquals(params, CoinageSubmissionParams.decodeTransfer(policy.params).getOrThrow())
    }

    /** No window is how a transfer asks never to be retried, so it must not come back as some window. */
    @Test
    fun `a transfer without a retry window survives a round trip`() {
        val params = TransferSubmissionParams(retryUntil = null)

        val policy = CoinageSubmissionParams.unloadPolicy(params)

        assertEquals(params, CoinageSubmissionParams.decodeTransfer(policy.params).getOrThrow())
    }

    @Test
    fun `a claim survives a round trip`() {
        val params = ClaimRetryParams(retryUntil = RETRY_UNTIL, receivedKey = RECEIVED_KEY)

        val policy = CoinageSubmissionParams.claimPolicy(params)

        assertEquals(params, CoinageSubmissionParams.decodeClaim(policy.params).getOrThrow())
    }

    @Test
    fun `each policy is keyed by its own id`() {
        val transfer = TransferSubmissionParams(RETRY_UNTIL)

        assertEquals(COINAGE_SPLIT_POLICY_ID, CoinageSubmissionParams.splitPolicy(transfer).id)
        assertEquals(COINAGE_UNLOAD_POLICY_ID, CoinageSubmissionParams.unloadPolicy(transfer).id)
        assertEquals(COINAGE_CLAIM_POLICY_ID, CoinageSubmissionParams.claimPolicy(ClaimRetryParams(RETRY_UNTIL, RECEIVED_KEY)).id)
    }

    // ---- conformance ----

    @Test
    fun `a transfer with a retry window encodes as a present optional millisecond`() {
        val encoded = CoinageSubmissionParams.splitPolicy(TransferSubmissionParams(RETRY_UNTIL)).params.value

        assertEquals(TRANSFER_WITH_WINDOW, encoded.toHex())
    }

    @Test
    fun `a transfer without a retry window encodes as an absent optional`() {
        val encoded = CoinageSubmissionParams.splitPolicy(TransferSubmissionParams(null)).params.value

        assertEquals(TRANSFER_WITHOUT_WINDOW, encoded.toHex())
    }

    @Test
    fun `a claim encodes its window followed by the received key`() {
        val encoded = CoinageSubmissionParams.claimPolicy(ClaimRetryParams(RETRY_UNTIL, RECEIVED_KEY)).params.value

        assertEquals(CLAIM, encoded.toHex())
    }

    @Test
    fun `stored params decode back to what was written`() {
        assertEquals(
            TransferSubmissionParams(RETRY_UNTIL),
            CoinageSubmissionParams.decodeTransfer(TRANSFER_WITH_WINDOW.fromHex().toDataByteArray()).getOrThrow(),
        )
        assertEquals(
            TransferSubmissionParams(null),
            CoinageSubmissionParams.decodeTransfer(TRANSFER_WITHOUT_WINDOW.fromHex().toDataByteArray()).getOrThrow(),
        )
        assertEquals(
            ClaimRetryParams(RETRY_UNTIL, RECEIVED_KEY),
            CoinageSubmissionParams.decodeClaim(CLAIM.fromHex().toDataByteArray()).getOrThrow(),
        )
    }

    /** Corruption is reported rather than swallowed: the policy decides what an unreadable row means. */
    @Test
    fun `unreadable params decode as a failure`() {
        assertTrue(CoinageSubmissionParams.decodeClaim(byteArrayOf(0x01).toDataByteArray()).isFailure)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        val RETRY_UNTIL: Instant = Instant.fromEpochMilliseconds(1_700_000_000_123)
        val RECEIVED_KEY: CoinPrivateKey = byteArrayOf(0x0a, 0x0b, 0x0c).toDataByteArray()

        const val TRANSFER_WITH_WINDOW = "017b68e5cf8b010000"
        const val TRANSFER_WITHOUT_WINDOW = "00"
        const val CLAIM = "7b68e5cf8b0100000c0a0b0c"
    }
}
