package io.paritytech.polkadotapp.feature_coinage_impl.domain

import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.feature_coinage_api.domain.UnloadDelayStrategy
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

class RandomUnloadDelayStrategy @Inject constructor() : UnloadDelayStrategy {
    companion object {
        private val MAX_DELAY = 6.hours
    }

    override fun calculateDelayUnloadUntil(): Timestamp {
        val randomDelay = Random.nextLong(from = 0L, until = MAX_DELAY.inWholeMilliseconds)
        return System.currentTimeMillis() + randomDelay
    }
}
