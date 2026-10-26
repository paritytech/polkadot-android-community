package io.paritytech.polkadotapp.design.utils

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.paritytech.polkadotapp.design.utils.SensitiveScreenHost.Companion.SECRET
import io.paritytech.polkadotapp.design.utils.SensitiveScreenHost.Companion.VISIBLE_TEXT
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectSensitiveScreenAccessibilityTest {
    private lateinit var host: SensitiveScreenHost

    @Before
    fun launchHost() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        host = SensitiveScreenHost()
    }

    @After
    fun closeHost() {
        if (::host.isInitialized) {
            host.close()
        }
    }

    @Test
    fun marksScreenSensitiveForAccessibilityWhenScreenCallsProtectSensitiveScreen() {
        host.showScreen(callsEffect = true)

        assertTrue(host.target.localView!!.isAccessibilityDataSensitive)
    }

    @Test
    fun marksSheetSensitiveForAccessibilityWhenSheetContentCallsProtectSensitiveScreen() {
        host.showSheet()

        assertTrue(host.target.localView!!.isAccessibilityDataSensitive)
    }

    @Test
    fun leavesActivityHostUnmarkedWhenOnlySheetContentCallsProtectSensitiveScreen() {
        host.showSheet()

        assertFalse(host.hostTarget.localView!!.isAccessibilityDataSensitive)
    }

    @Test
    fun marksAccessibilityEventsSensitiveWhenScreenCallsProtectSensitiveScreen() {
        host.showScreen(callsEffect = true)

        assertTrue(host.sendAccessibilityEvent(host.target).isAccessibilityDataSensitive)
    }

    @Test
    fun leavesAccessibilityEventsAloneWhenProtectSensitiveScreenLeavesComposition() {
        host.showScreen(callsEffect = true)
        host.leaveScreen()

        assertFalse(host.sendAccessibilityEvent(host.target).isAccessibilityDataSensitive)
    }

    @Test
    fun hidesScreenTextFromNonToolClientWhenScreenCallsProtectSensitiveScreen() {
        host.showScreen(callsEffect = true)

        val texts = host.windowTexts(host.nonToolClient())

        assertFalse("the client read the screen: $texts", texts.any { SECRET in it })
    }

    @Test
    fun showsScreenTextToToolClientWhenScreenCallsProtectSensitiveScreen() {
        host.showScreen(callsEffect = true)

        assertTrue(host.windowTexts(host.toolClient()).any { SECRET in it })
    }

    @Test
    fun showsScreenTextToNonToolClientWhenScreenDoesNotCallProtectSensitiveScreen() {
        host.showScreen(callsEffect = false)

        assertTrue(host.windowTexts(host.nonToolClient()).any { SECRET in it })
    }

    @Test
    fun hidesMnemonicWordFromNonToolClientWhenWordIsMarkedSensitive() {
        host.showMnemonicWord()

        val texts = host.windowTexts(host.nonToolClient())

        assertTrue("the client read nothing from the window: $texts", texts.any { VISIBLE_TEXT in it })
        assertFalse("the client read the word: $texts", texts.any { SECRET in it })
    }

    @Test
    fun showsTextBesideMnemonicWordToNonToolClientWhenWordIsMarkedSensitive() {
        host.showMnemonicWord()

        assertTrue(host.windowTexts(host.nonToolClient()).any { VISIBLE_TEXT in it })
    }

    @Test
    fun showsMnemonicWordToToolClientWhenWordIsMarkedSensitive() {
        host.showMnemonicWord()

        assertTrue(host.windowTexts(host.toolClient()).any { SECRET in it })
    }

    @Test
    fun withholdsContentChangesFromNonToolClientWhenScreenCallsProtectSensitiveScreen() {
        val client = host.nonToolClient()
        host.showScreen(callsEffect = true)

        val sensitivity = host.contentChangesSeenBy(client)

        assertTrue("the client got content changes: $sensitivity", sensitivity.isEmpty())
    }

    @Test
    fun deliversContentChangesToNonToolClientWhenScreenDoesNotCallProtectSensitiveScreen() {
        val client = host.nonToolClient()
        host.showScreen(callsEffect = false)

        assertTrue(host.contentChangesSeenBy(client).isNotEmpty())
    }

    @Test
    fun deliversSensitiveContentChangesToToolClientWhenScreenCallsProtectSensitiveScreen() {
        val client = host.toolClient()
        host.showScreen(callsEffect = true)

        val sensitivity = host.contentChangesSeenBy(client)

        assertTrue("no content change reached the tool client", sensitivity.isNotEmpty())
        assertTrue("unmarked content changes: $sensitivity", sensitivity.all { it })
    }
}
