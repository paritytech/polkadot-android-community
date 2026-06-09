package io.paritytech.polkadotapp.feature_upgrade_username_impl.presentation.upgrade

import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.feature_usernames_api.presentation.model.UsernameFieldState
import kotlinx.coroutines.flow.StateFlow

interface UpgradeUsernameContract {
    val uiState: StateFlow<UpgradeUsernameUiState>

    fun onUsernameChanged(value: String)

    fun onClaimAction()

    fun onBackClick()
}

@Immutable
data class UpgradeUsernameUiState(
    val username: String = "",
    val fieldState: UsernameFieldState = UsernameFieldState.NEUTRAL,
    val isClaimingInProgress: Boolean = false,
)
