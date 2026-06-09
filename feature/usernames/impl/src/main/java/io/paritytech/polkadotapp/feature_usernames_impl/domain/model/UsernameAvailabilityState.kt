package io.paritytech.polkadotapp.feature_usernames_impl.domain.model

sealed interface UsernameAvailabilityState {
    data class Available(val availableDigits: List<String>) : UsernameAvailabilityState
    data object Invalid : UsernameAvailabilityState
    data object Taken : UsernameAvailabilityState

    companion object {
        fun fromStatusAndDigits(status: String?, availableDigits: List<String>) = when (status) {
            "AVAILABLE" -> Available(availableDigits)
            "EXHAUSTED" -> Taken
            else -> Invalid
        }
    }
}
