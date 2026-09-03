package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.scoring

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import org.junit.Assert.assertEquals
import org.junit.Test

class LivenessScorerTest {

    private val scorer = LivenessScorer()
    private val blockTime = 2_000L // 2s -> plateau 4s, zero 20s

    @Test
    fun `perfect when nothing observed yet`() {
        assertEquals(
            ChainHealthScore.Perfect,
            scorer.score(blockTime, averageLatencyMillis = null, elapsedSinceLastBlockMillis = null),
        )
    }

    @Test
    fun `full score within the plateau`() {
        assertEquals(ChainHealthScore.MAX_VALUE, scorer.score(blockTime, 2_000L, 2_000L).value)
        assertEquals(ChainHealthScore.MAX_VALUE, scorer.score(blockTime, 4_000L, 0L).value)
    }

    @Test
    fun `zero score at or beyond the zero point`() {
        assertEquals(ChainHealthScore.MIN_VALUE, scorer.score(blockTime, 20_000L, 0L).value)
        assertEquals(ChainHealthScore.MIN_VALUE, scorer.score(blockTime, 30_000L, 0L).value)
    }

    @Test
    fun `linearly interpolates between plateau and zero`() {
        // midpoint of [4s, 20s] is 12s -> 50
        assertEquals(50, scorer.score(blockTime, 12_000L, 0L).value)
    }

    @Test
    fun `takes the worse of average latency and elapsed since last block`() {
        // avg healthy but a long stall since last block should still tank the score
        assertEquals(50, scorer.score(blockTime, averageLatencyMillis = 2_000L, elapsedSinceLastBlockMillis = 12_000L).value)
    }
}
