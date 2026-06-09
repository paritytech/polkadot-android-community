package io.paritytech.polkadotapp.feature_usernames_impl.data.claim

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.mapError
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.FoundUser
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username
import io.paritytech.polkadotapp.feature_usernames_impl.data.claim.network.api.UsernameApi
import io.paritytech.polkadotapp.feature_usernames_impl.data.claim.network.api.model.UsernameAvailableRequest
import io.paritytech.polkadotapp.feature_usernames_impl.data.claim.network.api.model.UsernameClaimRequest
import io.paritytech.polkadotapp.feature_usernames_impl.data.claim.network.api.model.toDomain
import io.paritytech.polkadotapp.feature_usernames_impl.domain.UsernamesChainProvider
import io.paritytech.polkadotapp.feature_usernames_impl.domain.model.ClaimUsernameParams
import io.paritytech.polkadotapp.feature_usernames_impl.domain.model.UsernameAvailabilityState
import io.paritytech.polkadotapp.tools_integrity_api.exception.mapToIntegrityIfNeeded
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface UsernameRepository {
    suspend fun checkUsernameAvailable(username: Username): Result<UsernameAvailabilityState>

    suspend fun claimUsername(params: ClaimUsernameParams): Result<Username>

    suspend fun searchUsernames(query: String): Result<List<FoundUser>>

    suspend fun getVerifier(): Result<String>
}

class RealUsernameRepository @Inject constructor(
    private val api: UsernameApi,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val usernamesChainProvider: UsernamesChainProvider,
) : UsernameRepository {
    override suspend fun getVerifier(): Result<String> {
        return withContext(coroutineDispatchers.io) {
            runCatching {
                api.getAttester().attester
            }
        }
    }

    override suspend fun checkUsernameAvailable(username: Username): Result<UsernameAvailabilityState> {
        return withContext(coroutineDispatchers.io) {
            runCatching {
                val usernameString = username.getDisplayUsername()
                val response = api.checkUsername(UsernameAvailableRequest(listOf(usernameString)))
                val entry = response.value[usernameString]
                // API returns digits as integers (e.g. 1, 42) — pad to 2-digit strings ("01", "42")
                // to match the preferredDigits format expected by the claim endpoint.
                val availableDigits = entry?.availableDigits
                    ?.map { it.toString().padStart(2, '0') }
                    .orEmpty()
                UsernameAvailabilityState.fromStatusAndDigits(entry?.status, availableDigits)
            }
        }
    }

    override suspend fun searchUsernames(query: String): Result<List<FoundUser>> {
        return withContext(coroutineDispatchers.io) {
            runCatching {
                val chain = usernamesChainProvider.chain()
                val usernames = api.searchUsernames(query).usernames
                usernames
                    .filterNot { it.accountId.isNullOrEmpty() }
                    .mapNotNull { it.toDomain(chain) }
            }
        }
    }

    override suspend fun claimUsername(params: ClaimUsernameParams): Result<Username> {
        return withContext(coroutineDispatchers.io) {
            runCatching {
                val request = UsernameClaimRequest(
                    candidateAccountId = params.candidateAddress,
                    username = params.username,
                    candidateSignature = params.candidateSignature.toHexString(true),
                    identifierKey = params.identifierKey.value.toHexString(true),
                    ringVrfKey = params.ringVrfKey.toHexString(true),
                    proofOfOwnership = params.membershipSignature.toHexString(true),
                    consumerRegistrationSignature = params.consumerSignature.toHexString(true),
                    preferredDigits = params.preferredDigits.ifEmpty { null },
                )

                api.claimUsername(request)
            }
                .map { Username.fromFullValue(it.username) }
                .mapError(::mapToIntegrityIfNeeded)
        }
    }
}
