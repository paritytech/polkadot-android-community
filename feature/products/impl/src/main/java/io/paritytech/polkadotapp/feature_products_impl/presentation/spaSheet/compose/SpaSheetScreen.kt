package io.paritytech.polkadotapp.feature_products_impl.presentation.spaSheet.compose

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetSurface
import io.paritytech.polkadotapp.design.components.error.DefaultErrorState
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import io.paritytech.polkadotapp.feature_products_impl.presentation.compose.DotNsLoadProgressCircle
import io.paritytech.polkadotapp.feature_products_impl.presentation.compose.ProductWebViewHost
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaSheet.SpaSheetUiState
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaSheet.SpaSheetViewModel
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun SpaSheetScreen(viewModel: SpaSheetViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val webView by viewModel.webView.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.onBackPressed()
    }

    SpaSheetScreenInternal(
        state = state,
        webView = webView,
    )
}

@Composable
private fun SpaSheetScreenInternal(
    state: SpaSheetUiState,
    webView: WebView?,
) {
    // No chrome at all: the product owns the sheet, so it reads as a native screen rather than a page.
    NovaBottomSheetSurface {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SHEET_HEIGHT_FRACTION),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isContentVisible -> ProductWebViewHost(
                    modifier = Modifier.fillMaxSize(),
                    webView = webView,
                )

                state.loadProgress is DotNsLoadProgress.Failed -> DefaultErrorState(
                    modifier = Modifier.fillMaxSize(),
                    text = stringResource(RCommon.string.product_resolution_error_unknown),
                )

                else -> DotNsLoadProgressCircle(progress = state.loadProgress)
            }
        }
    }
}

private const val SHEET_HEIGHT_FRACTION = 0.75f

@Preview
@Composable
private fun SpaSheetScreenPreview() {
    PolkadotTheme {
        SpaSheetScreenInternal(
            state = SpaSheetUiState(loadProgress = DotNsLoadProgress.Downloading(0.4f)),
            webView = null,
        )
    }
}
