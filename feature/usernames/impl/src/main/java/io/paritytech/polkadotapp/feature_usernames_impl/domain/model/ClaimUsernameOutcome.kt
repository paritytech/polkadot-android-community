package io.paritytech.polkadotapp.feature_usernames_impl.domain.model

sealed interface ClaimUsernameOutcome {
    data object Claimed : ClaimUsernameOutcome
    data class SuffixTaken(val freshDigits: List<String>) : ClaimUsernameOutcome
    data object Unavailable : ClaimUsernameOutcome
    data class Failed(val error: Throwable) : ClaimUsernameOutcome
}
