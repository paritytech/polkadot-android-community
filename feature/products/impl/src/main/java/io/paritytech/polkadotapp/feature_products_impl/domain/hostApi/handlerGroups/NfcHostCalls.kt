package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups

import io.paritytech.polkadotapp.common.domain.nfc.NfcPaymentRequest
import io.paritytech.polkadotapp.feature_products_impl.domain.jsEngine.ContainerBridge

/**
 * Host calls letting a product set/clear the deeplink the NFC HCE service presents to a tapping
 * phone — exposed to products as `window.host.ext.nfc.present(uri)` / `.clear()`.
 *
 * Mirrors the printer host-extension. The SPA calls `present(uri)` on its payment screen (same URI
 * it encodes in the QR) and `clear()` when leaving, so a customer can tap-to-pay.
 */
class NfcHostCalls : HostCallHandlerGroup {
    override fun registerOn(bridge: ContainerBridge) {
        bridge.registerHandler<NfcPresentDto, Unit>("nfcPresent") { params ->
            NfcPaymentRequest.uri = params.uri
            Result.success(Unit)
        }
        bridge.registerHandler<Unit, Unit>("nfcClear") {
            NfcPaymentRequest.uri = null
            Result.success(Unit)
        }
    }
}

private data class NfcPresentDto(val uri: String)
