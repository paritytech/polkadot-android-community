package io.paritytech.polkadotapp.app.nfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import io.paritytech.polkadotapp.common.domain.nfc.NfcPaymentRequest

/**
 * Debug-only trigger to set/clear the NFC payment payload over adb, so the NFC tap flow can be
 * tested with a real deeplink before the t3rminal SPA wires window.host.ext.nfc.present().
 * Inert in non-debuggable (release) builds. MUST be removed/guarded before any production release.
 *
 *   adb shell am broadcast -a io.paritytech.polkadotapp.nfc.PRESENT --es uri "polkadotapp://pay/cheque?..."
 *   adb shell am broadcast -a io.paritytech.polkadotapp.nfc.CLEAR
 */
class NfcTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Log.i(TAG, "onReceive action=${intent.action} debuggable=$debuggable")
        if (!debuggable) return
        when (intent.action) {
            ACTION_PRESENT -> {
                val uri = intent.getStringExtra(EXTRA_URI)
                NfcPaymentRequest.uri = uri
                Log.i(TAG, "payment URI set to $uri")
            }
            ACTION_CLEAR -> {
                NfcPaymentRequest.uri = null
                Log.i(TAG, "payment URI cleared")
            }
        }
    }

    private companion object {
        const val TAG = "NfcTestReceiver"
        const val ACTION_PRESENT = "io.paritytech.polkadotapp.nfc.PRESENT"
        const val ACTION_CLEAR = "io.paritytech.polkadotapp.nfc.CLEAR"
        const val EXTRA_URI = "uri"
    }
}
