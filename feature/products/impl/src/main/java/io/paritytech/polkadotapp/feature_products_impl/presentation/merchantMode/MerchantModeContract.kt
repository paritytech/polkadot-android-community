package io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode

import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportDisplay
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import kotlinx.coroutines.flow.StateFlow

interface MerchantModeContract {
    val state: StateFlow<MerchantModeUiState>

    val stalenessReport: StalenessReportDisplay

    fun onCloseClick()

    fun onBackPressed()
}

data class MerchantModeUiState(
    val loadProgress: DotNsLoadProgress,
    val pageState: MerchantModePageState,
)

sealed interface MerchantModePageState {
    data object Loading : MerchantModePageState
    data object Content : MerchantModePageState

    data object Unavailable : MerchantModePageState
}
