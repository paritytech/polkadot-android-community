package io.paritytech.polkadotapp.feature_usernames_impl.data.claim.network.api.model

import androidx.annotation.Keep

@Keep
class UsernameClaimRequest(
    val candidateAccountId: String,
    val username: String,
    val candidateSignature: String,
    val ringVrfKey: String,
    val proofOfOwnership: String,
    val consumerRegistrationSignature: String,
    val identifierKey: String,
    val preferredDigits: String? = null,
)
