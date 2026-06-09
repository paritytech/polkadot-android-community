package io.paritytech.polkadotapp.feature_products_impl.presentation.list

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.loading.mapLoading
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.withLoading
import io.paritytech.polkadotapp.feature_products_api.model.Product
import io.paritytech.polkadotapp.feature_products_impl.domain.ProductListInteractor
import io.paritytech.polkadotapp.feature_products_impl.presentation.list.models.ProductListItemUiModel
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProductListViewModel @Inject constructor(
    private val interactor: ProductListInteractor,
    private val router: ProductsRouter
) : BaseViewModel() {
    val state = interactor.observeProducts()
        .withLoading()
        .mapLoading { list -> list.toUi() }
        .stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)

    private fun List<Product>.toUi(): List<ProductListItemUiModel> {
        return map { product ->
            ProductListItemUiModel(
                id = product.id,
                name = product.name,
            )
        }
    }

    fun onProductSelected(product: ProductListItemUiModel) {
        router.openProductSettings(product.id)
    }

    fun onBack() {
        router.back()
    }
}
