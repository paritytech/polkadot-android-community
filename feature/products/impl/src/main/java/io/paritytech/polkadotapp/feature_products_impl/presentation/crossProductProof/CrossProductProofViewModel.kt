package io.paritytech.polkadotapp.feature_products_impl.presentation.crossProductProof

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.domain.model.asUtf8OrHex
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.asDisplayString
import io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof.CrossProductProofContext
import io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof.CrossProductProofContextHolder
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CrossProductProofViewModel @Inject constructor(
    private val router: ProductsRouter,
    private val context: CrossProductProofContext,
    private val holder: CrossProductProofContextHolder,
) : BaseViewModel() {
    val state: StateFlow<CrossProductProofUiState>
        field = MutableStateFlow(
            CrossProductProofUiState(
                callingProduct = context.callingProduct.value,
                onBehalfOf = context.onBehalfOf.value,
                suffix = context.suffix.asDisplayString(),
                message = context.message.asUtf8OrHex(),
            )
        )

    private var deciding = false

    fun onApproveClicked() = decide(context::deliverApproved)

    fun onRejectClicked() = decide(context::deliverRejected)

    // A second tap would pop whatever is under the sheet.
    private fun decide(answer: () -> Unit) = launchUnit {
        if (deciding) return@launchUnit
        deciding = true
        answer()
        router.back()
    }

    override fun onCleared() {
        super.onCleared()
        context.onAbandoned()
        holder.clear(context)
    }
}

data class CrossProductProofUiState(
    val callingProduct: String,
    val onBehalfOf: String,
    val suffix: String,
    val message: String,
)
