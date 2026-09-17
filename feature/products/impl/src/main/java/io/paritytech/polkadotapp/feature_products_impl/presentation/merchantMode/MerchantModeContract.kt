package io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode

import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportDisplay
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import kotlinx.coroutines.flow.StateFlow

interface MerchantModeContract {
    val state: StateFlow<MerchantModePageState>

    val stalenessReport: StalenessReportDisplay

    fun onCloseClick()

    fun onBackPressed()
}

sealed interface MerchantModePageState {
    data class Loading(val progress: DotNsLoadProgress) : MerchantModePageState

    data object Content : MerchantModePageState

    data object Unavailable : MerchantModePageState
}
