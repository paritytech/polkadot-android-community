package io.paritytech.polkadotapp.feature_coinage_api.domain.recycling

import io.paritytech.polkadotapp.common.utils.Fraction
import io.paritytech.polkadotapp.common.utils.Fraction.Companion.percents
import kotlin.math.max

/** The privacy strategies offered to the user. */
enum class RecyclingStrategyType {
    MIN_PRIVACY,
    BALANCED,
    MAX_PRIVACY
}

/**
 * [forcedRecyclingAge] is the age at which the chain stops accepting a coin. It is both the point every
 * strategy must recycle by and the anchor the softer thresholds are expressed against, so the presets track
 * it rather than hardcoding ages of their own.
 */
fun RecyclingStrategyType.paramsFor(forcedRecyclingAge: Int): RecyclingParams = when (this) {
    RecyclingStrategyType.MIN_PRIVACY -> RecyclingParams(
        maxUnavailableBalance = Fraction.ZERO,
        minRecyclingAge = forcedRecyclingAge,
        requiredRingFill = Fraction.ZERO,
        // Nothing is ever held back under this strategy, so the offer has nothing to apply to.
        allowsConfirmedSpend = true,
    )

    RecyclingStrategyType.BALANCED -> RecyclingParams(
        maxUnavailableBalance = BALANCED_UNAVAILABLE_BALANCE,
        minRecyclingAge = max(MIN_RECYCLABLE_AGE, forcedRecyclingAge / BALANCED_AGE_DIVISOR),
        requiredRingFill = BALANCED_RING_FILL,
        allowsConfirmedSpend = true,
    )

    RecyclingStrategyType.MAX_PRIVACY -> RecyclingParams(
        maxUnavailableBalance = Fraction.FULL,
        minRecyclingAge = MIN_RECYCLABLE_AGE,
        requiredRingFill = Fraction.FULL,
        allowsConfirmedSpend = false,
    )
}

/**
 * An unload mints its successor at age 1, so no coin younger than this exists on chain to recycle. Maximum
 * privacy sitting here means a recycled coin's successor is eligible again immediately — that is the point.
 */
private const val MIN_RECYCLABLE_AGE = 1

private const val BALANCED_AGE_DIVISOR = 3

private val BALANCED_UNAVAILABLE_BALANCE = 20.percents

private val BALANCED_RING_FILL = 50.percents
