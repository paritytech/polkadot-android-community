package io.paritytech.polkadotapp.feature_usernames_impl.data.claim

import androidx.annotation.Keep
import com.google.gson.Gson
import io.paritytech.polkadotapp.tools_jwt_auth_api.domain.error.toBackendRequestError
import retrofit2.HttpException

private const val HTTP_CONFLICT = 409

class UsernameAlreadyClaimedException : RuntimeException()

class QueueRateLimitedException(val retryAfterSeconds: Long?) : RuntimeException()

fun mapClaimUsernameError(throwable: Throwable): Throwable = when {
    // 409 is control flow, not an error surface — the interactor recovers a fresh suffix from it.
    throwable is HttpException && throwable.response()?.code() == HTTP_CONFLICT -> UsernameAlreadyClaimedException()

    else -> throwable.toBackendRequestError()
}

private const val HTTP_FORBIDDEN = 403
private const val DEVICE_EVIDENCE_INVALID = "DEVICE_EVIDENCE_INVALID"

// Reading consumes the error body.
fun Throwable.isDeviceEvidenceInvalid(gson: Gson): Boolean {
    if (this !is HttpException || code() != HTTP_FORBIDDEN) return false
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull() ?: return false
    val parsed = runCatching { gson.fromJson(body, ClaimErrorResponse::class.java) }.getOrNull()
    return parsed?.error == DEVICE_EVIDENCE_INVALID
}

@Keep
private class ClaimErrorResponse(
    val error: String?
)
