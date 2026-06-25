package io.paritytech.polkadotapp.app.kiosk

import android.app.admin.DeviceAdminReceiver

/**
 * Device-admin receiver that lets this app become a Device Owner and drive Android Lock Task
 * (true OS-level kiosk) on enterprise POS builds.
 *
 * Provision on a device with NO accounts and no existing owner:
 *   adb shell dpm set-device-owner <applicationId>/io.paritytech.polkadotapp.app.kiosk.KioskDeviceAdminReceiver
 *
 * Recovery (testOnly builds): adb shell dpm remove-active-admin <applicationId>/io.paritytech.polkadotapp.app.kiosk.KioskDeviceAdminReceiver
 */
class KioskDeviceAdminReceiver : DeviceAdminReceiver()
