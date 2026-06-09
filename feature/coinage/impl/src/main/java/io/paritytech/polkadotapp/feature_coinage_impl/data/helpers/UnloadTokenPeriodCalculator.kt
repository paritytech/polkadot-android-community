package io.paritytech.polkadotapp.feature_coinage_impl.data.helpers

import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

interface UnloadTokenPeriodCalculator {
    fun validPeriods(periodDuration: Duration): List<Long>
}

class RealUnloadTokenPeriodCalculator @Inject constructor() : UnloadTokenPeriodCalculator {
    override fun validPeriods(periodDuration: Duration): List<Long> {
        val currentTime = System.currentTimeMillis()

        val now = currentTime.milliseconds.coerceAtLeast(Duration.ZERO)

        val currentPeriod = now.inWholeSeconds / periodDuration.inWholeSeconds

        val oneHourAgo = (now - 1.hours).coerceAtLeast(Duration.ZERO)
        val oldPeriod = oneHourAgo.inWholeSeconds / periodDuration.inWholeSeconds

        return if (oldPeriod != currentPeriod) {
            listOf(oldPeriod, currentPeriod)
        } else {
            listOf(currentPeriod)
        }
    }
}
