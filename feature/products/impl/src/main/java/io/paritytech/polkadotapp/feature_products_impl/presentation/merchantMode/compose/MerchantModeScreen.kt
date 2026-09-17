package io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode.compose

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.common.utils.progressStallReport.StallReportContent
import io.paritytech.polkadotapp.common.utils.progressStallReport.previewStallReportOperations
import io.paritytech.polkadotapp.common.utils.progressStallReport.previewStallReportSteps
import io.paritytech.polkadotapp.design.components.error.DefaultErrorState
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Close
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.topbar.PolkadotTopBar
import io.paritytech.polkadotapp.design.components.topbar.rememberTopBarAction
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import io.paritytech.polkadotapp.feature_products_impl.presentation.compose.DotNsLoadProgressCircle
import io.paritytech.polkadotapp.feature_products_impl.presentation.compose.ProductWebViewHost
import io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode.MerchantModePageState
import io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode.MerchantModeUiState
import io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode.MerchantModeViewModel
import kotlinx.collections.immutable.persistentListOf
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun MerchantModeScreen(viewModel: MerchantModeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val webView by viewModel.webView.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.onBackPressed()
    }

    MerchantModeScreenInternal(
        state = state,
        webView = webView,
        stallReport = { viewModel.stalenessReport.DisplayReport() },
        onCloseClick = viewModel::onCloseClick,
    )
}

@Composable
private fun MerchantModeScreenInternal(
    state: MerchantModeUiState,
    webView: WebView?,
    stallReport: @Composable () -> Unit,
    onCloseClick: () -> Unit,
) {
    PolkadotSurface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PolkadotTopBar(
                title = stringResource(RCommon.string.settings_merchant_mode),
                actions = persistentListOf(
                    rememberTopBarAction(action = onCloseClick, icon = NovaIcons.Close)
                ),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                when (state.pageState) {
                    MerchantModePageState.Content -> ProductWebViewHost(
                        modifier = Modifier.fillMaxSize(),
                        webView = webView,
                    )

                    MerchantModePageState.Unavailable -> DefaultErrorState(
                        modifier = Modifier.fillMaxSize(),
                        text = stringResource(RCommon.string.merchant_mode_unavailable),
                    )

                    MerchantModePageState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DotNsLoadProgressCircle(progress = state.loadProgress)

                        stallReport()
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MerchantModeScreenPreview() {
    PolkadotTheme {
        MerchantModeScreenInternal(
            state = MerchantModeUiState(
                loadProgress = DotNsLoadProgress.Downloading(0.4f),
                pageState = MerchantModePageState.Loading,
            ),
            webView = null,
            stallReport = { PreviewStallReport() },
            onCloseClick = {},
        )
    }
}

@Composable
private fun PreviewStallReport() {
    StallReportContent(
        runningOperations = previewStallReportOperations(),
        steps = previewStallReportSteps(),
    )
}
