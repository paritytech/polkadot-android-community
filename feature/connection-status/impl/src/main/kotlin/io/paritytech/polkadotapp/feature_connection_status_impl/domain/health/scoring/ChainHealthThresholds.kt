package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.scoring

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * All tunable chain-health constants in one place. These are heuristic starting points chosen to
 * keep a healthy chain pinned at 100 (a full white ring) and only degrade once a metric breaches
 * its tolerance — retune here after observing behaviour on live networks.
 */
object ChainHealthThresholds {

    // --- Block liveness (latency), relative to the chain's expected block time ---
    // Full score while average latency stays within this multiple of the block time.
    const val LIVENESS_PLATEAU_MULTIPLIER = 2.0
    // Zero score once latency reaches this multiple of the block time.
    const val LIVENESS_ZERO_MULTIPLIER = 10.0
    // Samples in the moving-average latency window.
    const val LATENCY_WINDOW_SIZE = 10
    // Cadence at which liveness re-evaluates with no new block, so the ring depletes during a stall.
    val LIVENESS_TICK: Duration = 1.seconds

    // --- Finality gap, in blocks (best - finalized). Per-chain overrides live in Chain.additional
    // (finalityGapIdeal / finalityGapOutage); these are the fallback defaults. ---
    // Full score up to this gap; GRANDPA is structurally >= 2 behind and async backing adds a few more.
    const val FINALITY_GAP_IDEAL = 6
    // Zero score once the gap reaches this many blocks. A stalling finality grows the gap, so
    // magnitude alone already captures monotonic finality stall.
    const val FINALITY_GAP_OUTAGE = 24
}
