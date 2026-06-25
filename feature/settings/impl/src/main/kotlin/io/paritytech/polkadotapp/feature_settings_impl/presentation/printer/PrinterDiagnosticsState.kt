package io.paritytech.polkadotapp.feature_settings_impl.presentation.printer

import androidx.compose.runtime.Immutable

@Immutable
data class PrinterDiagnosticsState(
    val isPrinterAvailable: Boolean = false,
    val isPrinterTestInProgress: Boolean = false,
    val printerTestMessage: PrinterTestMessage? = null,
)

@Immutable
data class PrinterTestMessage(
    val id: Long,
    val success: Boolean,
    val errorMessage: String? = null,
)
