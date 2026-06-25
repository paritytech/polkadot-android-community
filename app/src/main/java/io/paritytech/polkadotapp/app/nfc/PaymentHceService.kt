package io.paritytech.polkadotapp.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import io.paritytech.polkadotapp.common.domain.nfc.NfcPaymentRequest

/**
 * Quick NFC proof-of-concept: emulates an NDEF Type-4 tag (via Host Card Emulation) that exposes a
 * single URI record. When a customer taps their phone to this Sunmi terminal, the phone reads the
 * NDEF URI and opens the Polkadot app at the payment deeplink — the NFC analog of showing the
 * payment QR.
 *
 * Emits (static PoC): polkadotapp://pay/cheque?id=...&amount=9.00&key=...
 *
 * Verify the service is registered:
 *   adb shell dumpsys nfc | grep -i polkadotapp
 *
 * NOTE: the customer's phone only auto-opens the app if the consumer Polkadot app registers an
 * NFC handler (NDEF_DISCOVERED / an Android Application Record) for the polkadotapp:// scheme.
 */
class PaymentHceService : HostApduService() {

    private var selectedFile: SelectedFile = SelectedFile.NONE

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SW_INSTRUCTION_NOT_SUPPORTED
        return when {
            apdu.startsWith(SELECT_NDEF_APP) -> {
                selectedFile = SelectedFile.NONE
                SW_OK
            }
            apdu.startsWith(SELECT_CC_FILE) -> {
                selectedFile = SelectedFile.CC
                SW_OK
            }
            apdu.startsWith(SELECT_NDEF_FILE) -> {
                selectedFile = SelectedFile.NDEF
                SW_OK
            }
            apdu.size >= 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB0.toByte() -> readBinary(apdu)
            else -> SW_INSTRUCTION_NOT_SUPPORTED
        }
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = SelectedFile.NONE
    }

    private fun readBinary(apdu: ByteArray): ByteArray {
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val length = apdu[4].toInt() and 0xFF
        val source = when (selectedFile) {
            SelectedFile.CC -> CC_FILE
            SelectedFile.NDEF -> buildNdefFile()
            SelectedFile.NONE -> return SW_INSTRUCTION_NOT_SUPPORTED
        }
        if (offset >= source.size) return SW_OK
        val end = minOf(offset + length, source.size)
        return source.copyOfRange(offset, end) + SW_OK
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    /**
     * NDEF file = [NLEN hi, NLEN lo] + NDEF message, built from the currently-presented payment URI
     * ([NfcPaymentRequest.uri]). Returns an empty NDEF (NLEN = 0) when no request is active, so a
     * tap reads nothing. Built per read since the URI can change between taps.
     */
    private fun buildNdefFile(): ByteArray {
        val uri = NfcPaymentRequest.uri ?: return byteArrayOf(0x00, 0x00)

        val uriBytes = uri.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(uriBytes.size + 1)
        payload[0] = 0x00 // URI identifier code 0x00 = no abbreviation (full URI follows)
        System.arraycopy(uriBytes, 0, payload, 1, uriBytes.size)

        // Record header: MB=1, ME=1, SR=1, TNF=0x01 (well-known) -> 0xD1; type len 1; payload len; 'U'
        val record = byteArrayOf(0xD1.toByte(), 0x01, payload.size.toByte(), 0x55) + payload
        val nlen = record.size
        return byteArrayOf((nlen shr 8).toByte(), (nlen and 0xFF).toByte()) + record
    }

    private enum class SelectedFile { NONE, CC, NDEF }

    private companion object {
        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_INSTRUCTION_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00)

        // SELECT (by name) NDEF Type-4 application: 00 A4 04 00 07 D2760000850101  (Le ignored)
        val SELECT_NDEF_APP = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )
        // SELECT (by file id) Capability Container: 00 A4 00 0C 02 E103
        val SELECT_CC_FILE = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03)
        // SELECT (by file id) NDEF file: 00 A4 00 0C 02 E104
        val SELECT_NDEF_FILE = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)

        // Capability Container: NDEF file E104, max size 0x7FFF, read allowed (00), write denied (FF)
        val CC_FILE = byteArrayOf(
            0x00, 0x0F, 0x20, 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte(),
            0x04, 0x06, 0xE1.toByte(), 0x04, 0x7F, 0xFF.toByte(), 0x00, 0xFF.toByte(),
        )
    }
}
