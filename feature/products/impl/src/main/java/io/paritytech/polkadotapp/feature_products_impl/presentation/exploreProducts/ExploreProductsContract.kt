package io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts

import android.webkit.WebView
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportDisplay
import kotlinx.coroutines.flow.StateFlow

interface ExploreProductsContract {
    val state: StateFlow<LoadingState<ExploreProductsContent>>

    val stalenessReport: StalenessReportDisplay

    fun onRetry()

    fun onSkip()

    fun pauseConnections()

    fun resumeConnections()
}

sealed interface ExploreProductsContent {
    data class Page(val webView: WebView) : ExploreProductsContent

    data object Skipped : ExploreProductsContent
}
