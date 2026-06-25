package io.paritytech.polkadotapp.common.domain.nfc

/**
 * Holds the payment deeplink the NFC HCE service currently presents to a tapping phone.
 *
 * Set by the product host-extension (window.host.ext.nfc, once the container app wires it) or the
 * debug test trigger; read by PaymentHceService when a phone reads the emulated NDEF tag.
 * `null` means no active payment request (the terminal emits an empty tag).
 */
object NfcPaymentRequest {
    @Volatile
    var uri: String? = null
}
