package io.paritytech.polkadotapp.feature_settings_impl.presentation.forceReclaim

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ForceReclaimContract {
    val state: StateFlow<ForceReclaimUiState>

    val reclaimEvents: SharedFlow<ForceReclaimEvent>

    fun onReclaimClick()

    fun onBackClick()
}

data class ForceReclaimUiState(
    val isReclaiming: Boolean,
)
