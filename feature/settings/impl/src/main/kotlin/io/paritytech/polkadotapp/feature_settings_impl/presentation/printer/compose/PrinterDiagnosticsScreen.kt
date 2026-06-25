package io.paritytech.polkadotapp.feature_settings_impl.presentation.printer.compose

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.components.topbar.PolkadotTopBar
import io.paritytech.polkadotapp.design.components.topbar.TopBarTitleAlignment
import io.paritytech.polkadotapp.design.components.topbar.rememberTopBarAction
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_settings_impl.presentation.printer.PrinterDiagnosticsState
import io.paritytech.polkadotapp.feature_settings_impl.presentation.printer.PrinterDiagnosticsViewModel
import io.paritytech.polkadotapp.feature_settings_impl.presentation.printer.PrinterTestMessage
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun PrinterDiagnosticsScreen(viewModel: PrinterDiagnosticsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val printerTestMessage = state.printerTestMessage
    val printerTestToast = printerTestMessage?.let {
        if (it.success) {
            stringResource(RCommon.string.settings_print_test_success)
        } else {
            stringResource(
                RCommon.string.settings_print_test_failed,
                it.errorMessage ?: stringResource(RCommon.string.settings_print_test_unknown_error)
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPrinterStatus()
    }

    LaunchedEffect(printerTestMessage?.id) {
        if (printerTestMessage != null && printerTestToast != null) {
            Toast.makeText(context, printerTestToast, Toast.LENGTH_SHORT).show()
            viewModel.onPrinterTestMessageShown()
        }
    }

    PrinterDiagnosticsScreenInternal(
        state = state,
        onBackClick = viewModel::onBackClick,
        onPrintTestClick = viewModel::onPrintTestClick,
    )
}

@Composable
private fun PrinterDiagnosticsScreenInternal(
    state: PrinterDiagnosticsState,
    onBackClick: () -> Unit,
    onPrintTestClick: () -> Unit,
) {
    PolkadotSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            PolkadotTopBar(
                title = stringResource(RCommon.string.settings_printer),
                navigationAction = rememberTopBarAction(onBackClick),
                titleAlignment = TopBarTitleAlignment.Center,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PolkadotTheme.spacings.large)
            ) {
                VerticalSpacer { large }

                NovaText(
                    text = stringResource(RCommon.string.settings_printer_status),
                    style = PolkadotTheme.typography.body.large
                )

                VerticalSpacer { small }

                NovaText(
                    text = if (state.isPrinterAvailable) {
                        stringResource(RCommon.string.settings_printer_available)
                    } else {
                        stringResource(RCommon.string.settings_printer_unavailable)
                    },
                    color = if (state.isPrinterAvailable) {
                        PolkadotTheme.colors.fg.primary
                    } else {
                        PolkadotTheme.colors.fg.warning
                    },
                    style = PolkadotTheme.typography.body.mediumEmphasized
                )

                VerticalSpacer { large }

                PolkadotTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(RCommon.string.settings_print_test),
                    enabled = state.isPrinterAvailable,
                    loading = state.isPrinterTestInProgress,
                    onClick = onPrintTestClick,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PrinterDiagnosticsScreenPreview() {
    PolkadotTheme {
        PrinterDiagnosticsScreenInternal(
            state = PrinterDiagnosticsState(
                isPrinterAvailable = true,
                printerTestMessage = PrinterTestMessage(id = 1, success = true),
            ),
            onBackClick = {},
            onPrintTestClick = {},
        )
    }
}
