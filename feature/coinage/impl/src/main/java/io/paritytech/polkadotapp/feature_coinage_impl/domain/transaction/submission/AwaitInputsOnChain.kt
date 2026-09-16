package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How long a look holds out for every input once some of them are visible.
 *
 * Holding out is deliberate: an input still landing is the ordinary reason a look is incomplete, and building
 * half of a group now would put the rest in a second call for nothing. Settling for the last look is equally
 * deliberate — an input that never arrives must not hold up the ones that did.
 */
private val HOLD_OUT = 30.seconds

/**
 * How long one call waits while nothing it asks about is visible. Returning then costs the executor another
 * call, and keeps a deadline that passes meanwhile from going unnoticed until the next look.
 */
private val IDLE_LIMIT = 5.minutes

/** What one wait for inputs established. */
@OptIn(ExperimentalTime::class)
class InputsLook<K>(
    val present: Set<K>,
    /** Whether any look was taken at all: only a look can say an input is absent. */
    val looked: Boolean,
    val takenAt: Instant,
) {
    /** Proven absent past its [deadline], which is the only thing that ends a transaction's retries. */
    fun abandoned(input: K, deadline: Instant): Boolean = looked && input !in present && takenAt >= deadline
}

/**
 * The inputs of [wanted] that [presence] shows, once every one of them is visible, once [HOLD_OUT] has passed
 * since some of them were, or once a look was taken after [earliestDeadline].
 *
 * [presence] reports the whole set it can see on each look; a look it cannot take must not be emitted, so a
 * failed read never erases what the chain last showed. Each look is consumed once, so a caller that acts on a
 * partial look and asks again waits for the chain to change rather than spinning on the same answer.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
suspend fun <K> awaitInputs(
    presence: Flow<Set<K>>,
    wanted: Set<K>,
    earliestDeadline: Instant,
    timeProvider: TimeProvider,
): InputsLook<K> = coroutineScope {
    val looks = presence.produceIn(this)

    try {
        var latest: Set<K>? = null

        withTimeoutOrNull(IDLE_LIMIT) {
            while (true) {
                val look = looks.receive().intersect(wanted)
                latest = look

                when {
                    look.containsAll(wanted) -> break
                    look.isNotEmpty() -> {
                        latest = holdOut(looks, wanted, look)
                        break
                    }
                    timeProvider.now() >= earliestDeadline -> break
                }
            }
        }

        InputsLook(present = latest.orEmpty(), looked = latest != null, takenAt = timeProvider.now())
    } finally {
        looks.cancel()
    }
}

private suspend fun <K> holdOut(
    looks: ReceiveChannel<Set<K>>,
    wanted: Set<K>,
    first: Set<K>,
): Set<K> {
    var latest = first

    withTimeoutOrNull(HOLD_OUT) {
        while (!latest.containsAll(wanted)) {
            // The newest look wins outright, even when it holds fewer inputs than the one before: a fork can
            // take one away, and building against the widest view ever seen would spend what is no longer there.
            latest = looks.receive().intersect(wanted)
        }
    }

    return latest
}
