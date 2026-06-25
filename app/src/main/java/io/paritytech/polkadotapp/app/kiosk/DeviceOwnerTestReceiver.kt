package io.paritytech.polkadotapp.app.kiosk

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Debug-only trigger to relinquish this app's Device Owner status over adb.
 *
 * adb cannot remove a non-test device owner on a user build (uninstall / pm clear /
 * remove-active-admin are all refused), but the owner app *itself* can drop it. After this clears
 * the device owner, the app can be uninstalled and its data wiped normally. Inert in non-debuggable
 * (release) builds. MUST stay debug-guarded — never ship an unguarded device-owner relinquish.
 *
 *   adb shell am broadcast -n io.paritytech.polkadotapp.nightly/io.paritytech.polkadotapp.app.kiosk.DeviceOwnerTestReceiver -a io.paritytech.polkadotapp.kiosk.CLEAR_OWNER
 */
class DeviceOwnerTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Log.i(TAG, "onReceive action=${intent.action} debuggable=$debuggable")
        if (!debuggable || intent.action != ACTION_CLEAR_OWNER) return

        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.i(TAG, "not device owner; nothing to clear")
            return
        }
        runCatching {
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(context.packageName)
        }
            .onSuccess {
                Log.i(TAG, "device owner cleared for ${context.packageName}")
                // Also drop the active admin so the app uninstalls without an admin block.
                val admin = ComponentName(context, KioskDeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(admin)) {
                    runCatching { dpm.removeActiveAdmin(admin) }
                        .onSuccess { Log.i(TAG, "active admin removed") }
                        .onFailure { Log.e(TAG, "removeActiveAdmin failed", it) }
                }
            }
            .onFailure { Log.e(TAG, "clearDeviceOwnerApp failed", it) }
    }

    private companion object {
        const val TAG = "DeviceOwnerTest"
        const val ACTION_CLEAR_OWNER = "io.paritytech.polkadotapp.kiosk.CLEAR_OWNER"
    }
}
