@file:OptIn(ExperimentalMaterial3Api::class)

package io.paritytech.polkadotapp.design.utils

import android.app.UiAutomation
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaModalBottomSheet
import io.paritytech.polkadotapp.design.components.mnemonic.MnemonicWord
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ProtectSensitiveScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private var isProtected by mutableStateOf(true)
    private var hasLeftScreen = false
    private var secretText by mutableStateOf(SECRET)
    private var clicks = 0
    private var hostClicks = 0
    private val target = Target()
    private val hostTarget = Target()

    @Before
    fun launchHost() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    @After
    fun closeHost() {
        scenario.close()
    }

    @Test
    fun ignoresObscuredTapWhenScreenCallsProtectSensitiveScreen() {
        showScreen(callsEffect = true)

        tap(target, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(0, clicks)
    }

    @Test
    fun takesUnobscuredTapWhenScreenCallsProtectSensitiveScreen() {
        showScreen(callsEffect = true)

        tap(target, flags = 0)

        assertEquals(1, clicks)
    }

    @Test
    fun takesObscuredTapWhenScreenDoesNotCallProtectSensitiveScreen() {
        showScreen(callsEffect = false)

        tap(target, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(1, clicks)
    }

    @Test
    fun ignoresObscuredTapInSheetWhenSheetContentCallsProtectSensitiveScreen() {
        showSheet()

        tap(target, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(0, clicks)
    }

    @Test
    fun takesObscuredTapInHostWhenOnlySheetContentCallsProtectSensitiveScreen() {
        showSheet()

        tap(hostTarget, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(1, hostClicks)
    }

    @Test
    fun takesObscuredTapAgainWhenProtectSensitiveScreenLeavesComposition() {
        showScreen(callsEffect = true)
        leaveScreen()

        tap(target, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)

        assertEquals(1, clicks)
    }

    @Test
    fun ignoresTapInjectedByNonToolAccessibilityServiceWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = true)

        tap(target, flags = FLAG_IS_ACCESSIBILITY_EVENT)

        assertEquals(0, clicks)
    }

    @Test
    fun takesTapInjectedByAccessibilityToolWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = true)

        tap(target, flags = FLAG_IS_ACCESSIBILITY_EVENT or FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL)

        assertEquals(1, clicks)
    }

    @Test
    fun takesTapInjectedByNonToolAccessibilityServiceWhenScreenDoesNotCallProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = false)

        tap(target, flags = FLAG_IS_ACCESSIBILITY_EVENT)

        assertEquals(1, clicks)
    }

    @Test
    fun takesTapInjectedByAccessibilityToolWhenScreenDoesNotCallProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = false)

        tap(target, flags = FLAG_IS_ACCESSIBILITY_EVENT or FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL)

        assertEquals(1, clicks)
    }

    @Test
    fun marksScreenSensitiveForAccessibilityWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = true)

        assertTrue(target.localView!!.isAccessibilityDataSensitive)
    }

    @Test
    fun marksSheetSensitiveForAccessibilityWhenSheetContentCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showSheet()

        assertTrue(target.localView!!.isAccessibilityDataSensitive)
    }

    @Test
    fun leavesActivityHostUnmarkedWhenOnlySheetContentCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showSheet()

        assertFalse(hostTarget.localView!!.isAccessibilityDataSensitive)
    }

    @Test
    fun marksAccessibilityEventsSensitiveWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = true)

        assertTrue(sendAccessibilityEvent(target).isAccessibilityDataSensitive)
    }

    @Test
    fun leavesAccessibilityEventsAloneWhenProtectSensitiveScreenLeavesComposition() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = true)
        leaveScreen()

        assertFalse(sendAccessibilityEvent(target).isAccessibilityDataSensitive)
    }

    @Test
    fun hidesScreenTextFromNonToolClientWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = true)

        assertFalse(windowTexts(nonToolClient()).any { SECRET in it })
    }

    @Test
    fun showsScreenTextToToolClientWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = true)

        assertTrue(windowTexts(toolClient()).any { SECRET in it })
    }

    @Test
    fun showsScreenTextToNonToolClientWhenScreenDoesNotCallProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = false)

        assertTrue(windowTexts(nonToolClient()).any { SECRET in it })
    }

    @Test
    fun hidesMnemonicWordFromNonToolClient() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showMnemonicWord()

        assertFalse(windowTexts(nonToolClient()).any { SECRET in it })
    }

    @Test
    fun showsTextBesideMnemonicWordToNonToolClient() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showMnemonicWord()

        assertTrue(windowTexts(nonToolClient()).any { VISIBLE_TEXT in it })
    }

    @Test
    fun showsMnemonicWordToToolClient() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showMnemonicWord()

        assertTrue(windowTexts(toolClient()).any { SECRET in it })
    }

    @Test
    fun withholdsContentChangesFromNonToolClientWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = true)

        assertTrue(contentChangesSeenBy(nonToolClient()).isEmpty())
    }

    @Test
    fun deliversContentChangesToNonToolClientWhenScreenDoesNotCallProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = false)

        assertTrue(contentChangesSeenBy(nonToolClient()).isNotEmpty())
    }

    @Test
    fun deliversSensitiveContentChangesToToolClientWhenScreenCallsProtectSensitiveScreen() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        showScreen(callsEffect = true)

        val sensitivity = contentChangesSeenBy(toolClient())

        assertTrue(sensitivity.isNotEmpty() && sensitivity.all { it })
    }

    private fun showScreen(callsEffect: Boolean) {
        setContent {
            if (callsEffect && isProtected) {
                ProtectSensitiveScreen()
                DisposableEffect(Unit) {
                    onDispose { hasLeftScreen = true }
                }
            }
            Column {
                BasicText(secretText)
                ClickTarget(target, onClick = { clicks++ })
            }
        }
        awaitPlaced(target)
    }

    private fun showSheet() {
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PolkadotTheme {
                    ClickTarget(hostTarget, onClick = { hostClicks++ })

                    NovaModalBottomSheet(isVisible = true, onDismissRequest = {}) {
                        ProtectSensitiveScreen()
                        ClickTarget(target, onClick = { clicks++ })
                    }
                }
            }
        }
        awaitPlaced(hostTarget)
        awaitPlaced(target)
    }

    private fun showMnemonicWord() {
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PolkadotTheme {
                    Column {
                        BasicText(VISIBLE_TEXT)
                        MnemonicWord(word = SECRET)
                        ClickTarget(target, onClick = {})
                    }
                }
            }
        }
        awaitPlaced(target)
    }

    private fun setContent(content: @Composable () -> Unit) {
        scenario.onActivity { activity ->
            activity.setContentView(ComposeView(activity).apply { setContent(content) })
        }
    }

    @Composable
    private fun ClickTarget(target: Target, onClick: () -> Unit) {
        val localView = LocalView.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TARGET_HEIGHT_DP.dp)
                .onGloballyPositioned { coordinates ->
                    target.localView = localView
                    target.centre = coordinates.boundsInWindow().center
                }
                .clickable(onClick = onClick)
        )
    }

    private fun leaveScreen() {
        instrumentation.runOnMainSync {
            isProtected = false
            Snapshot.sendApplyNotifications()
        }
        awaitCondition("the screen never left composition") { hasLeftScreen }
    }

    private fun awaitPlaced(target: Target) {
        var lastCentre: Offset? = null
        awaitCondition("the click target was never placed") {
            val centre = target.centre
            val isSettled = centre != null && centre == lastCentre && target.localView?.isAttachedToWindow == true
            lastCentre = centre
            isSettled
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        fail(message)
    }

    private fun tap(target: Target, flags: Int) {
        val rootView = target.localView!!.rootView
        val centre = target.centre!!
        val downTime = SystemClock.uptimeMillis()
        instrumentation.runOnMainSync {
            rootView.dispatchTouchEvent(touchEvent(downTime, MotionEvent.ACTION_DOWN, centre, flags))
            rootView.dispatchTouchEvent(touchEvent(downTime, MotionEvent.ACTION_UP, centre, flags))
        }
        instrumentation.waitForIdleSync()
    }

    private fun sendAccessibilityEvent(target: Target): AccessibilityEvent {
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
        val localView = target.localView!!
        instrumentation.runOnMainSync {
            (localView.parent as ComposeView).onRequestSendAccessibilityEvent(localView, event)
        }
        return event
    }

    private fun toolClient(): UiAutomation = instrumentation.getUiAutomation(0).also { client ->
        assertTrue(client.serviceInfo.isAccessibilityTool)
    }

    private fun nonToolClient(): UiAutomation = instrumentation.getUiAutomation(FLAG_NOT_ACCESSIBILITY_TOOL).also { client ->
        assumeFalse("this device ignores FLAG_NOT_ACCESSIBILITY_TOOL", client.serviceInfo.isAccessibilityTool)
    }

    private fun windowTexts(client: UiAutomation): List<String> {
        var root: AccessibilityNodeInfo? = null
        awaitCondition("no window reached the client") {
            root = client.rootInActiveWindow
            root != null
        }
        return collectTexts(root!!)
    }

    private fun collectTexts(node: AccessibilityNodeInfo): List<String> {
        val own = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        val children = (0 until node.childCount).mapNotNull(node::getChild).flatMap(::collectTexts)
        return own + children
    }

    private fun contentChangesSeenBy(client: UiAutomation): List<Boolean> {
        SystemClock.sleep(CONNECTION_SETTLE_MS)
        windowTexts(client)
        val sensitivity = CopyOnWriteArrayList<Boolean>()
        client.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                sensitivity += event.isAccessibilityDataSensitive
            }
        }
        instrumentation.runOnMainSync {
            secretText = CHANGED_SECRET
            Snapshot.sendApplyNotifications()
        }
        SystemClock.sleep(EVENT_WINDOW_MS)
        client.setOnAccessibilityEventListener(null)
        return sensitivity.toList()
    }

    private fun touchEvent(downTime: Long, action: Int, position: Offset, flags: Int): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = position.x
            y = position.y
            pressure = 1f
            size = 1f
        }
        return MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            1,
            arrayOf(properties),
            arrayOf(coords),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            flags,
        )
    }

    private class Target {
        var localView: View? = null
        var centre: Offset? = null
    }

    private companion object {
        // @hide MotionEvent.FLAG_IS_ACCESSIBILITY_EVENT and FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL
        const val FLAG_IS_ACCESSIBILITY_EVENT = 0x800
        const val FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL = 0x1000

        // @TestApi UiAutomation.FLAG_NOT_ACCESSIBILITY_TOOL
        const val FLAG_NOT_ACCESSIBILITY_TOOL = 0x4

        const val SECRET = "harbor velvet"
        const val CHANGED_SECRET = "meadow signal"
        const val VISIBLE_TEXT = "Recovery phrase"
        const val TARGET_HEIGHT_DP = 120
        const val SETTLE_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 50L
        const val CONNECTION_SETTLE_MS = 3_000L
        const val EVENT_WINDOW_MS = 4_000L
    }
}
