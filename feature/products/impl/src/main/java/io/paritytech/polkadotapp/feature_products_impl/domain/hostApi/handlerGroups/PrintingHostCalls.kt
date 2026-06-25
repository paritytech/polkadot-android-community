package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups

import io.paritytech.polkadotapp.common.domain.printing.PrintDocument
import io.paritytech.polkadotapp.common.domain.printing.PrintDocumentKind
import io.paritytech.polkadotapp.common.domain.printing.PrintItem
import io.paritytech.polkadotapp.common.domain.printing.PrintLine
import io.paritytech.polkadotapp.common.domain.printing.PrintQr
import io.paritytech.polkadotapp.common.domain.printing.PrinterPaperWidth
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApi
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.CallingProductIdProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.jsEngine.ContainerBridge

class PrintingHostCalls(
    private val botApi: ProductsBotApi,
    private val callingProductIdProvider: CallingProductIdProvider,
) : HostCallHandlerGroup {
    override fun registerOn(bridge: ContainerBridge) {
        bridge.registerHandler<Unit, PrinterAvailabilityDto>("printerIsAvailable") {
            Result.success(PrinterAvailabilityDto(available = botApi.isPrinterAvailable()))
        }

        bridge.registerHandler<PrintDocumentDto, Unit>("printerPrint") { params ->
            val productId = callingProductIdProvider.getProductId().getOrThrow()
            botApi.print(productId, params.toDomain())
        }
    }
}

private data class PrinterAvailabilityDto(val available: Boolean)

private data class PrintDocumentDto(
    val kind: String,
    val paperWidth: String? = null,
    val title: String,
    val subtitle: String? = null,
    val header: List<PrintLineDto>? = null,
    val body: List<PrintLineDto>? = null,
    val items: List<PrintItemDto>? = null,
    val totals: List<PrintLineDto>? = null,
    val qr: PrintQrDto? = null,
    val footer: List<String>? = null,
) {
    fun toDomain(): PrintDocument = PrintDocument(
        kind = kind.toPrintKind(),
        paperWidth = paperWidth.toPaperWidth(),
        title = title,
        subtitle = subtitle,
        header = header.orEmpty().map { it.toDomain() },
        body = body.orEmpty().map { it.toDomain() },
        items = items.orEmpty().map { it.toDomain() },
        totals = totals.orEmpty().map { it.toDomain() },
        qr = qr?.toDomain(),
        footer = footer.orEmpty(),
    )
}

private data class PrintLineDto(
    val label: String,
    val value: String? = null,
) {
    fun toDomain(): PrintLine = PrintLine(label = label, value = value)
}

private data class PrintItemDto(
    val name: String,
    val quantity: String? = null,
    val total: String? = null,
) {
    fun toDomain(): PrintItem = PrintItem(name = name, quantity = quantity, total = total)
}

private data class PrintQrDto(
    val data: String,
    val label: String? = null,
    val moduleSize: Int? = null,
) {
    fun toDomain(): PrintQr = PrintQr(data = data, label = label, moduleSize = moduleSize)
}

private fun String.toPrintKind(): PrintDocumentKind = when (this) {
    "CustomerReceipt" -> PrintDocumentKind.CustomerReceipt
    "XReport" -> PrintDocumentKind.XReport
    "ZReport" -> PrintDocumentKind.ZReport
    else -> throw IllegalArgumentException("Unknown print document kind: $this")
}

private fun String?.toPaperWidth(): PrinterPaperWidth = when (this) {
    null, "Mm58" -> PrinterPaperWidth.Mm58
    "Mm80" -> PrinterPaperWidth.Mm80
    else -> throw IllegalArgumentException("Unknown printer paper width: $this")
}
