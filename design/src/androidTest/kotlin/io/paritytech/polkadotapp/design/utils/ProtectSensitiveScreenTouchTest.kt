package io.paritytech.polkadotapp.design.utils

import android.os.Build
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.paritytech.polkadotapp.design.utils.SensitiveScreenHost.Companion.FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL
import io.paritytech.polkadotapp.design.utils.SensitiveScreenHost.Companion.FLAG_IS_ACCESSIBILITY_EVENT
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectSensitiveScreenTouchTest {
    private lateinit var host: SensitiveScreenHost

    @Before
    fun launchHost() {
        host = SensitiveScreenHost()
    }

    @After
    fun closeHost() {
        host.close()
    }

    @Test
    fun ignoresObscuredTapWhenScreenCallsProtectSensitiveScreen() {
        host.showScreen(callsEffect = true)

        host.tap(host.target, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(0, host.clicks)
    }

    @Test
    fun takesUnobscuredTapWhenScreenCallsProtectSensitiveScreen() {
        host.showScreen(callsEffect = true)

        host.tap(host.target, flags = 0)

        assertEquals(1, host.clicks)
    }

    @Test
    fun takesObscuredTapWhenScreenDoesNotCallProtectSensitiveScreen() {
        host.showScreen(callsEffect = false)

        host.tap(host.target, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(1, host.clicks)
    }

    @Test
    fun ignoresObscuredTapInSheetWhenSheetContentCallsProtectSensitiveScreen() {
        host.showSheet()

        host.tap(host.target, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(0, host.clicks)
    }

    @Test
    fun takesObscuredTapInHostWhenOnlySheetContentCallsProtectSensitiveScreen() {
        host.showSheet()

        host.tap(host.hostTarget, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(1, host.hostClicks)
    }

    @Test
    fun takesObscuredTapAgainWhenProtectSensitiveScreenLeavesComposition() {
        host.showScreen(callsEffect = true)
        host.leaveScreen()

        host.tap(host.target, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(1, host.clicks)
    }

    @Test
    fun ignoresTapInjectedByNonToolAccessibilityServiceWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        host.showScreen(callsEffect = true)

        host.tap(host.target, flags = FLAG_IS_ACCESSIBILITY_EVENT)

        assertEquals(0, host.clicks)
    }

    @Test
    fun takesTapInjectedByAccessibilityToolWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        host.showScreen(callsEffect = true)

        host.tap(host.target, flags = FLAG_IS_ACCESSIBILITY_EVENT or FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL)

        assertEquals(1, host.clicks)
    }

    @Test
    fun takesTapInjectedByNonToolAccessibilityServiceWhenScreenDoesNotCallProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        host.showScreen(callsEffect = false)

        host.tap(host.target, flags = FLAG_IS_ACCESSIBILITY_EVENT)

        assertEquals(1, host.clicks)
    }

    @Test
    fun takesTapInjectedByAccessibilityToolWhenScreenDoesNotCallProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        host.showScreen(callsEffect = false)

        host.tap(host.target, flags = FLAG_IS_ACCESSIBILITY_EVENT or FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL)

        assertEquals(1, host.clicks)
    }
}
