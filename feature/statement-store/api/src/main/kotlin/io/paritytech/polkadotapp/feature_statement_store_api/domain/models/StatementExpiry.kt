package io.paritytech.polkadotapp.feature_statement_store_api.domain.models

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val U32_MAX: ULong = 0xFFFF_FFFFuL

object StatementExpiry {
    fun createForCurrentTimestamp(): ULong {
        val currentSeconds = StatementTimestamp.currentEpochSeconds()
        return createWithPriority(currentSeconds.toUInt())
    }

    fun createDefault(): ULong {
        return createWithPriority(0u)
    }

    fun createWithPriority(priority: UInt): ULong {
        return (U32_MAX shl 32) or priority.toULong()
    }

    /** Monotonic successor to [prev], floored at the current wall-clock priority (mirrors iOS `incrementedExpiry`). */
    fun nextAfter(prev: ULong): ULong = maxOf(prev + 1uL, createForCurrentTimestamp())

    fun createExpiringIn(lifetime: Duration): ULong {
        val expiresAtSeconds = System.currentTimeMillis().milliseconds.inWholeSeconds + lifetime.inWholeSeconds
        return expiresAtSeconds.toULong() shl 32
    }

    fun nextExpiringIn(prev: ULong, lifetime: Duration): ULong = maxOf(prev + 1uL, createExpiringIn(lifetime))

    fun nextAfter(prev: ULong, lifetime: StatementLifetime): ULong = when (lifetime) {
        StatementLifetime.Permanent -> nextAfter(prev)
        is StatementLifetime.Limited -> nextExpiringIn(prev, lifetime.duration)
    }
}
