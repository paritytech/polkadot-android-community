package io.paritytech.polkadotapp.common.domain.printing

enum class PrintDocumentKind {
    CustomerReceipt,
    XReport,
    ZReport,
}

enum class PrinterPaperWidth(val columns: Int) {
    Mm58(columns = 37),
    Mm80(columns = 48),
}

data class PrintDocument(
    val kind: PrintDocumentKind,
    val paperWidth: PrinterPaperWidth = PrinterPaperWidth.Mm58,
    val title: String,
    val subtitle: String? = null,
    val header: List<PrintLine> = emptyList(),
    val body: List<PrintLine> = emptyList(),
    val items: List<PrintItem> = emptyList(),
    val totals: List<PrintLine> = emptyList(),
    val qr: PrintQr? = null,
    val footer: List<String> = emptyList(),
)

data class PrintLine(
    val label: String,
    val value: String? = null,
)

data class PrintItem(
    val name: String,
    val quantity: String? = null,
    val total: String? = null,
)

data class PrintQr(
    val data: String,
    val label: String? = null,
    val moduleSize: Int? = null,
)
