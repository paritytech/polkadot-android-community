package io.paritytech.polkadotapp.common.domain.kiosk

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridges the in-app (soft) kiosk state to the host Activity's device-owner Lock Task.
 *
 * The SPA kiosk flips [engaged] to true while its kiosk is active; RootActivity observes this and
 * starts/stops Android Lock Task accordingly — but only when the app is Device Owner and the build
 * has kiosk lockdown enabled. So the device enters OS kiosk *only* while the app's kiosk is on,
 * and leaves it the moment the cashier exits with the PIN.
 */
object KioskLockController {
    val engaged = MutableStateFlow(false)
}
