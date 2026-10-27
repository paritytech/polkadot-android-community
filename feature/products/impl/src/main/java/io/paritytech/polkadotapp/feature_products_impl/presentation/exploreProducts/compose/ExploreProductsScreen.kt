package io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.utils.progressStallReport.StallReportContent
import io.paritytech.polkadotapp.common.utils.progressStallReport.previewStallReportOperations
import io.paritytech.polkadotapp.common.utils.progressStallReport.previewStallReportSteps
import io.paritytech.polkadotapp.design.components.dialog.NovaAlertDialog
import io.paritytech.polkadotapp.design.components.navigationbar.LocalAppNavigationBarInsets
import io.paritytech.polkadotapp.design.components.progress.LoadingScreenState
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.topbar.PolkadotTopBar
import io.paritytech.polkadotapp.design.components.topbar.TopBarTitleSize
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreLoadError
import io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts.ExploreProductsContent
import io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts.ExploreProductsViewModel
import io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts.compose.components.ExploreProductsSetupError
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun ExploreProductsScreen() {
    val viewModel = hiltViewModel<ExploreProductsViewModel>()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resumeConnections() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pauseConnections() }

    val state by viewModel.state.collectAsStateWithLifecycle()

    if ((state as? LoadingState.Error)?.exception is ExploreLoadError.HostUnresolved) {
        ExploreProductsHostRetryDialog(
            onRetry = viewModel::onRetry,
            onSkip = viewModel::onSkip,
        )
    }

    ExploreProductsScreenInternal(
        state = state,
        stallReport = { viewModel.stalenessReport.DisplayReport() },
        onRetry = viewModel::onRetry,
    )
}

@Composable
private fun ExploreProductsHostRetryDialog(
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    NovaAlertDialog(
        title = stringResource(RCommon.string.common_error_general_title),
        text = stringResource(RCommon.string.explore_products_load_failed),
        positiveButtonTitle = stringResource(RCommon.string.common_retry),
        onPositiveButtonClick = onRetry,
        negativeButtonTitle = stringResource(RCommon.string.common_skip),
        onNegativeButtonClick = onSkip,
        onDismissRequest = onSkip,
    )
}

@Composable
private fun ExploreProductsScreenInternal(
    state: LoadingState<ExploreProductsContent>,
    stallReport: @Composable () -> Unit,
    onRetry: () -> Unit,
) {
    PolkadotSurface(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(LocalAppNavigationBarInsets.current)
        ) {
            PolkadotTopBar(
                title = stringResource(RCommon.string.bottom_nav_menu_explore),
                titleSize = TopBarTitleSize.Large,
            )

            when (state) {
                LoadingState.Loading -> Box(modifier = Modifier.fillMaxSize()) {
                    LoadingScreenState(modifier = Modifier.fillMaxSize())

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                horizontal = PolkadotTheme.spacings.extraLarge,
                                vertical = PolkadotTheme.spacings.large,
                            )
                    ) {
                        stallReport()
                    }
                }
                is LoadingState.Loaded -> when (val content = state.data) {
                    is ExploreProductsContent.Page -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { content.webView },
                    )
                    ExploreProductsContent.Skipped -> Unit
                }
                is LoadingState.Error -> when (val error = state.exception) {
                    is ExploreLoadError.SetupFailed -> ExploreProductsSetupError(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = PolkadotTheme.spacings.extraLarge),
                        kind = error.kind,
                        onRetry = onRetry,
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Preview
@Composable
private fun ExploreProductsScreenLoadingPreview() {
    PolkadotTheme {
        ExploreProductsScreenInternal(
            state = LoadingState.Loading,
            stallReport = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun ExploreProductsScreenStalledPreview() {
    PolkadotTheme {
        ExploreProductsScreenInternal(
            state = LoadingState.Loading,
            stallReport = {
                StallReportContent(
                    runningOperations = previewStallReportOperations(),
                    steps = previewStallReportSteps(),
                )
            },
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun ExploreProductsHostRetryDialogPreview() {
    PolkadotTheme {
        ExploreProductsHostRetryDialog(
            onRetry = {},
            onSkip = {},
        )
    }
}
