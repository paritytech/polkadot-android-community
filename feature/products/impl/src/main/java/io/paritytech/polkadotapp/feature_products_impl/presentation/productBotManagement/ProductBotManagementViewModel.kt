package io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.common.utils.mapList
import io.paritytech.polkadotapp.common.utils.requireSuffix
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.toUrl
import io.paritytech.polkadotapp.feature_products_impl.domain.productBotManagement.ProductBotManagementInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductBotManagementViewModel @Inject constructor(
    private val interactor: ProductBotManagementInteractor,
    private val router: ProductsRouter,
) : BaseViewModel(), ProductBotManagementContract {
    override val state = MutableStateFlow(ProductBotManagementState())

    init {
        interactor.observeProducts()
            .mapList { product ->
                @Suppress("DEPRECATION")
                ProductUiModel(
                    id = product.id,
                    name = product.name,
                    scriptUrl = product.scriptUrl,
                    appUrl = product.id.toUrl(),
                )
            }
            .onEach { products -> state.update { it.copy(products = products) } }
            .launchIn(this)
    }

    override fun onBackClick() {
        router.back()
    }

    override fun onAddProductClick() {
        state.update { it.copy(dialogState = ProductDialogState.Form()) }
    }

    override fun onProductClick(productId: ProductId) {
        launch {
            router.openSpaBrowser(productId)
        }
    }

    override fun onEditProductClick(productId: String) {
        val product = state.value.products.find { it.id.value == productId } ?: return
        state.update {
            it.copy(
                dialogState = ProductDialogState.Form(
                    productId = product.id.value,
                    dotNsName = product.name,
                    scriptUrl = product.scriptUrl,
                )
            )
        }
    }

    override fun onDeleteProductClick(productId: String) = launchUnit {
        interactor.deleteProduct(ProductId.fromStoredValue(productId))
            .onFailure(::showError)
    }

    override fun onDialogDismiss() {
        state.update { it.copy(dialogState = ProductDialogState.None) }
    }

    override fun onDotNsDomainChanged(dotNsDomain: String) {
        state.update {
            val form = it.dialogState as? ProductDialogState.Form ?: return@update it
            it.copy(dialogState = form.copy(dotNsName = dotNsDomain))
        }
    }

    override fun onScriptUrlChanged(url: String) {
        state.update {
            val form = it.dialogState as? ProductDialogState.Form ?: return@update it
            it.copy(dialogState = form.copy(scriptUrl = url))
        }
    }

    override fun onDialogConfirm() = launchUnit {
        val form = state.value.dialogState as? ProductDialogState.Form ?: return@launchUnit
        if (form.scriptUrl.isBlank() || form.dotNsName.isBlank()) return@launchUnit

        state.update {
            it.copy(dialogState = form.copy(isSubmitting = true))
        }

        val result = if (form.productId != null) {
            interactor.updateProduct(ProductId.fromStoredValue(form.productId), form.scriptUrl, form.dotNsName)
        } else {
            val dotNs = form.dotNsName.requireSuffix(".dot")
            val productId = ProductId.fromStoredValue(dotNs)
            interactor.upsertProduct(productId, form.scriptUrl, form.dotNsName)
        }

        result
            .onSuccess {
                state.update { it.copy(dialogState = ProductDialogState.None) }
            }
            .onFailure { error ->
                showError(error)

                state.update {
                    it.copy(dialogState = form.copy(isSubmitting = false))
                }
            }
    }
}
