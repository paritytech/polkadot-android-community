package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class CoinageLogFilterTest {
    @Test
    fun `keeps only coinage entries`() {
        val log = sequenceOf(
            "2026-09-07 10:00:00.000 Network DEBUG connected",
            "2026-09-07 10:00:01.000 CoinageTransfer INFO tx-submitted",
            "2026-09-07 10:00:02.000 Network DEBUG disconnected"
        )

        val filtered = log.filterCoinageLogEntries(maxLines = 100)

        assertEquals(listOf("2026-09-07 10:00:01.000 CoinageTransfer INFO tx-submitted"), filtered)
    }

    @Test
    fun `keeps stack trace lines that belong to a coinage entry`() {
        val log = sequenceOf(
            "2026-09-07 10:00:01.000 CoinageTransfer ERROR tx-failed",
            "java.lang.IllegalStateException: boom",
            "\tat io.paritytech.polkadotapp.Coinage.transfer(Coinage.kt:42)",
            "2026-09-07 10:00:02.000 Network DEBUG disconnected"
        )

        val filtered = log.filterCoinageLogEntries(maxLines = 100)

        assertEquals(3, filtered.size)
        assertEquals("\tat io.paritytech.polkadotapp.Coinage.transfer(Coinage.kt:42)", filtered.last())
    }

    @Test
    fun `drops stack trace lines that belong to another tag`() {
        val log = sequenceOf(
            "2026-09-07 10:00:01.000 Network ERROR request-failed",
            "java.net.SocketTimeoutException: timeout"
        )

        val filtered = log.filterCoinageLogEntries(maxLines = 100)

        assertEquals(emptyList<String>(), filtered)
    }

    @Test
    fun `keeps the most recent lines when the cap is reached`() {
        val log = (1..10).asSequence()
            .map { "2026-09-07 10:00:0$it.000 CoinageTransfer INFO entry-$it" }

        val filtered = log.filterCoinageLogEntries(maxLines = 3)

        assertEquals(3, filtered.size)
        assertEquals("2026-09-07 10:00:010.000 CoinageTransfer INFO entry-10", filtered.last())
    }
}
