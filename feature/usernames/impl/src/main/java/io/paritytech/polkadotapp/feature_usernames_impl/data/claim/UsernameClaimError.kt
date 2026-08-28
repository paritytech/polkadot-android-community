package io.paritytech.polkadotapp.feature_usernames_impl.data.claim

import io.paritytech.polkadotapp.tools_integrity_api.exception.IntegrityException
import io.paritytech.polkadotapp.tools_integrity_api.exception.mapToIntegrityIfNeeded
import retrofit2.HttpException

class UsernameAlreadyClaimedException : RuntimeException()

class QueueRateLimitedException(val retryAfterSeconds: Long?) : RuntimeException()

fun mapClaimUsernameError(throwable: Throwable): Throwable {
    val integrityCause = throwable.findCause<IntegrityException>()
    return when {
        throwable is HttpException && throwable.response()?.code() == 409 -> UsernameAlreadyClaimedException()
        integrityCause != null -> integrityCause
        else -> mapToIntegrityIfNeeded(throwable)
    }
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
