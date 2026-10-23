package io.paritytech.polkadotapp.design.utils

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
fun KeepScreenOn() {
    val currentView = LocalView.current

    DisposableEffect(Unit) {
        val originalState = currentView.keepScreenOn

        currentView.keepScreenOn = true

        onDispose {
            currentView.keepScreenOn = originalState
        }
    }
}

@Composable
fun LockScreenOrientation() {
    LocalActivity.current?.let { activity ->
        DisposableEffect(Unit) {
            val originalOrientation = activity.requestedOrientation

            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

            onDispose {
                activity.requestedOrientation = originalOrientation
            }
        }
    }
}

@Composable
fun ProtectSensitiveScreen() {
    // AndroidComposeView never runs onFilterTouchEventForSecurity, so the flag goes on the view above it.
    val hostView = LocalView.current.parent as? ViewGroup

    if (hostView != null) {
        DisposableEffect(hostView) {
            val originalFilter = hostView.filterTouchesWhenObscured
            val originalDelegate = hostView.accessibilityDelegate

            hostView.filterTouchesWhenObscured = true
            hostView.accessibilityDelegate = SensitiveAccessibilityEvents

            onDispose {
                hostView.filterTouchesWhenObscured = originalFilter
                hostView.accessibilityDelegate = originalDelegate
            }
        }
    }
}

private object SensitiveAccessibilityEvents : View.AccessibilityDelegate() {
    override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            event.isAccessibilityDataSensitive = true
        }

        return super.onRequestSendAccessibilityEvent(host, child, event)
    }
}
