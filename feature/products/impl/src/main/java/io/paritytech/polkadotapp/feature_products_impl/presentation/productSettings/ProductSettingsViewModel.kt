package io.paritytech.polkadotapp.feature_products_impl.presentation.productSettings

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.withLoading
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.presentation.ProductSettingsPayload
import io.paritytech.polkadotapp.feature_products_impl.domain.productSettings.ProductSettingsInteractor
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.feature_products_impl.presentation.productSettings.models.ProductSettingsUiModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProductSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val interactor: ProductSettingsInteractor,
    private val router: ProductsRouter
) : BaseViewModel() {
    private val payload: ProductSettingsPayload = savedStateHandle.getPayload()
    private val productId = ProductId.fromStoredValue(payload.productId)

    val state: StateFlow<LoadingState<ProductSettingsUiModel>> = interactor.observeProduct(productId)
        .filterNotNull()
        .map { product ->
            ProductSettingsUiModel(
                name = product.name,
            )
        }
        .withLoading()
        .stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)

    fun onBack() {
        router.back()
    }

    fun onPermissionsClick() {
        router.openProductPermissions(productId)
    }
}
