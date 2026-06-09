package io.paritytech.polkadotapp.feature_products_impl.presentation.resourceAllocationRequest

import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import kotlinx.coroutines.flow.StateFlow

interface ResourceAllocationRequestContract {
    val state: StateFlow<LoadingState<ResourceAllocationRequestUiState>>

    fun onApproveClicked()

    fun onRejectClicked()
}

data class ResourceAllocationRequestUiState(
    val productId: String,
    val resourceLabels: List<Int>,
)
