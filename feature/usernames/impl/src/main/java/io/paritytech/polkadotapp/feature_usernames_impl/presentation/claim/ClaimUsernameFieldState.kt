package io.paritytech.polkadotapp.feature_usernames_impl.presentation.claim

import io.paritytech.polkadotapp.feature_usernames_impl.domain.error.UsernameFlowError

sealed interface ClaimUsernameFieldState {
    data object Neutral : ClaimUsernameFieldState
    data object Taken : ClaimUsernameFieldState
    data object Invalid : ClaimUsernameFieldState

    /** Not reachable today — kept so the existing copy survives if the backend starts sending it. */
    data object AlreadyCreated : ClaimUsernameFieldState

    data object Available : ClaimUsernameFieldState
    data class Error(val error: UsernameFlowError) : ClaimUsernameFieldState
}
