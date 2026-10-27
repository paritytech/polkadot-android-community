package io.paritytech.polkadotapp.feature_statement_store_impl.domain.models

import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.StatementExpiry
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.StatementLifetime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class StatementExpiryTest {
    @Test
    fun `limited lifetime expires after it and keeps increasing`() {
        val lifetime = StatementLifetime.Limited(15.minutes)
        val nowSeconds = System.currentTimeMillis() / 1000

        val first = StatementExpiry.nextAfter(0uL, lifetime)
        val second = StatementExpiry.nextAfter(first, lifetime)

        val expiresAtSeconds = (first shr 32).toLong()
        assertTrue(expiresAtSeconds - nowSeconds in 15.minutes.inWholeSeconds..15.minutes.inWholeSeconds + 1)
        assertTrue(second > first)
    }

    @Test
    fun `permanent lifetime never expires`() {
        val expiry = StatementExpiry.nextAfter(0uL, StatementLifetime.Permanent)

        assertEquals(0xFFFF_FFFFuL, expiry shr 32)
    }
}
