package io.paritytech.polkadotapp.feature_usernames_impl.data.claim

import io.paritytech.polkadotapp.tools_integrity_api.exception.mapToIntegrityIfNeeded
import retrofit2.HttpException

class UsernameAlreadyClaimedException : RuntimeException()

fun mapClaimUsernameError(throwable: Throwable): Throwable = when {
    throwable is HttpException && throwable.response()?.code() == 409 -> UsernameAlreadyClaimedException()
    else -> mapToIntegrityIfNeeded(throwable)
}
