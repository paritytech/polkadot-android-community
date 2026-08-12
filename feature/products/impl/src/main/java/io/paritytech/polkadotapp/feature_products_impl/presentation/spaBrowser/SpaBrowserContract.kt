package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import kotlinx.coroutines.flow.StateFlow

interface SpaBrowserContract {
    val state: StateFlow<SpaBrowserUiState>

    fun onBackPressed()
}

data class SpaBrowserUiState(
    val title: String? = "",
    val subtitle: String? = "",
    val loadProgress: DotNsLoadProgress = DotNsLoadProgress.Idle,
)
