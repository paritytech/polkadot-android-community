package io.paritytech.polkadotapp.feature_coinage_api.domain.model

import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object HandedOffCoinsClaimWindow {
    val duration = 6.hours

    @OptIn(ExperimentalTime::class)
    fun closesAt(handedOffAt: Instant): Instant = handedOffAt + duration
}
