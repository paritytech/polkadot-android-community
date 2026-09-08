package io.paritytech.polkadotapp.feature_coinage_api.domain.model

/**
 * One step a coin has taken since it left a recycler.
 *
 * Each hop costs the coin some of the anonymity it came out with, and how much depends on how many other
 * coins moved with it — a transfer of one coin on its own hides in nothing, a transfer of eight hides in
 * eight. The size of that crowd is what each variant carries.
 */
sealed interface Hop {
    /** [bundleSize] coins moved together in the transfer this hop records. */
    data class Transfer(val bundleSize: Int) : Hop

    /** [fanout] coins came out of the split this hop records, counting change. */
    data class Split(val fanout: Int) : Hop
}

/**
 * A hop's crowd size is specified as an unsigned 8-bit count, and both call sites derive it from a list
 * length, so they are coerced rather than validated: a bundle of one is the smallest honest answer, and a
 * bundle larger than the range can hold is indistinguishable from the largest it can.
 */
fun transferHop(bundleSize: Int) = Hop.Transfer(bundleSize.coerceIn(MIN_CROWD, MAX_CROWD))

fun splitHop(fanout: Int) = Hop.Split(fanout.coerceIn(MIN_CROWD, MAX_CROWD))

private const val MIN_CROWD = 1
private const val MAX_CROWD = 255
