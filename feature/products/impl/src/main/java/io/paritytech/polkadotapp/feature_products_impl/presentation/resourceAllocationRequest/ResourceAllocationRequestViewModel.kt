package io.paritytech.polkadotapp.feature_products_impl.presentation.resourceAllocationRequest

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.flowOf
import io.paritytech.polkadotapp.common.utils.inBackground
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.common.utils.withLoading
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest.ResourceAllocationRequestContext
import io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest.ResourceAllocationRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import io.paritytech.polkadotapp.common.R as RCommon

@HiltViewModel
class ResourceAllocationRequestViewModel @Inject constructor(
    private val router: ProductsRouter,
    private val context: ResourceAllocationRequestContext,
    private val holder: ResourceAllocationRequestContextHolder,
) : BaseViewModel(), ResourceAllocationRequestContract {
    override val state: StateFlow<LoadingState<ResourceAllocationRequestUiState>> = flowOf {
        Result.success(
            ResourceAllocationRequestUiState(
                productId = context.productId.value,
                resourceLabels = context.resources.map { it.labelRes() },
            )
        )
    }
        .withLoading("ResourceAllocationRequest")
        .inBackground()
        .stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)

    override fun onApproveClicked() = launchUnit {
        context.deliverApproved()
        router.back()
    }

    override fun onRejectClicked() = launchUnit {
        context.deliverRejected()
        router.back()
    }

    override fun onCleared() {
        super.onCleared()
        holder.clear()
    }
}

private fun ApAllocatableResource.labelRes(): Int = when (this) {
    ApAllocatableResource.BulletInAllowance -> RCommon.string.product_resource_allocation_bulletin
    ApAllocatableResource.StatementStoreAllowance -> RCommon.string.product_resource_allocation_statement_store
    is ApAllocatableResource.SmartContractAllowance -> RCommon.string.product_resource_allocation_smart_contract
    ApAllocatableResource.AutoSigning -> RCommon.string.product_resource_allocation_auto_signing
}
