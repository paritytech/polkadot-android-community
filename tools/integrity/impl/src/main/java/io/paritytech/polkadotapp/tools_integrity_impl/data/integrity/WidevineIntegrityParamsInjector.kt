package io.paritytech.polkadotapp.tools_integrity_impl.data.integrity

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.mapError
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.common.utils.toByteArray
import io.paritytech.polkadotapp.tools_integrity_api.claim.ClaimDeviceEvidence
import io.paritytech.polkadotapp.tools_integrity_api.claim.ClaimDeviceEvidenceProvider
import io.paritytech.polkadotapp.tools_integrity_api.domain.error.IntegrityError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStoreException
import java.security.ProviderException
import javax.inject.Inject

class WidevineIntegrityParamsInjector @Inject constructor(
    private val evidenceProvider: ClaimDeviceEvidenceProvider,
) {
    suspend operator fun invoke(request: Request): Result<Request> {
        return evidenceProvider.collectEvidence()
            .mapError(Throwable::toClaimIntegrityError)
            .flatMap { evidence ->
                if (evidence == null) {
                    Result.success(request)
                } else {
                    injectEvidence(request, evidence)
                        .mapError { IntegrityError.Unknown }
                }
            }
    }

    private fun injectEvidence(request: Request, evidence: ClaimDeviceEvidence): Result<Request> =
        runCancellableCatching {
            val originalBody = requireNotNull(request.body)
            val originalJson = widevineJson.decodeFromString(
                JsonObject.serializer(),
                originalBody.toByteArray().toString(Charsets.UTF_8),
            )
            val evidenceJson = widevineJson.encodeToJsonElement(
                ClaimEvidenceFields.serializer(),
                evidence.toFields(),
            ).jsonObject
            val replacementBody = widevineJson.encodeToString(
                JsonObject.serializer(),
                JsonObject(originalJson + evidenceJson),
            ).toRequestBody(originalBody.contentType())

            request.newBuilder()
                .method(request.method, replacementBody)
                .build()
        }

    private fun ClaimDeviceEvidence.toFields() = ClaimEvidenceFields(
        attestationChain = attestationChain,
        deviceChallenge = deviceChallenge,
        deviceId = deviceId,
    )
}

@Serializable
private class ClaimEvidenceFields(
    val attestationChain: List<String>,
    val deviceChallenge: String,
    val deviceId: String,
)

private val widevineJson = Json {
    ignoreUnknownKeys = true
}

private fun Throwable.toClaimIntegrityError(): IntegrityError = when (this) {
    is CancellationException -> throw this
    is IntegrityError -> this
    is IOException,
    is HttpException -> IntegrityError.AttestationTransient

    is ProviderException,
    is InvalidAlgorithmParameterException,
    is KeyStoreException,
    is UnsupportedOperationException -> IntegrityError.AttestationUnavailable

    else -> IntegrityError.Unknown
}
