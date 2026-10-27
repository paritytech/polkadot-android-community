@file:OptIn(ExperimentalMaterial3Api::class)

package io.paritytech.polkadotapp.design.utils

import android.app.UiAutomation
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
import androidx.test.platform.app.InstrumentationRegistry
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaModalBottomSheet
import io.paritytech.polkadotapp.design.components.mnemonic.MnemonicWord
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class SensitiveScreenHost {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val packageName = instrumentation.targetContext.packageName
    private val scenario = ActivityScenario.launch(ComponentActivity::class.java)

    private var isProtected by mutableStateOf(true)
    private var hasLeftScreen = false
    private var secretText by mutableStateOf(SECRET)

    var clicks = 0
        private set
    var hostClicks = 0
        private set
    val target = Target()
    val hostTarget = Target()

    fun close() {
        scenario.close()
    }

    fun showScreen(callsEffect: Boolean) {
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

    fun showSheet() {
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

    fun showMnemonicWord() {
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

    fun leaveScreen() {
        instrumentation.runOnMainSync {
            isProtected = false
            Snapshot.sendApplyNotifications()
        }
        awaitCondition("the screen never left composition") { hasLeftScreen }
    }

    fun tap(target: Target, flags: Int) {
        val rootView = target.localView!!.rootView
        val centre = target.centre!!
        val downTime = SystemClock.uptimeMillis()
        instrumentation.runOnMainSync {
            rootView.dispatchTouchEvent(touchEvent(downTime, MotionEvent.ACTION_DOWN, centre, flags))
            rootView.dispatchTouchEvent(touchEvent(downTime, MotionEvent.ACTION_UP, centre, flags))
        }
        instrumentation.waitForIdleSync()
    }

    fun sendAccessibilityEvent(target: Target): AccessibilityEvent {
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
        val localView = target.localView!!
        instrumentation.runOnMainSync {
            (localView.parent as ComposeView).onRequestSendAccessibilityEvent(localView, event)
        }
        return event
    }

    fun toolClient(): UiAutomation = instrumentation.getUiAutomation(TOOL_CLIENT_FLAGS).also { client ->
        assertTrue(client.serviceInfo.isAccessibilityTool)
    }

    fun nonToolClient(): UiAutomation = instrumentation.getUiAutomation(FLAG_NOT_ACCESSIBILITY_TOOL).also { client ->
        assertFalse("this device ignores FLAG_NOT_ACCESSIBILITY_TOOL", client.serviceInfo.isAccessibilityTool)
    }

    fun windowTexts(client: UiAutomation): List<String> {
        var root: AccessibilityNodeInfo? = null
        awaitCondition("the client never reached the test window") {
            root = client.rootInActiveWindow
            root?.packageName == packageName
        }
        return collectTexts(root!!)
    }

    fun contentChangesSeenBy(client: UiAutomation): List<Boolean> {
        SystemClock.sleep(CONNECTION_SETTLE.inWholeMilliseconds)
        windowTexts(client)
        val sensitivity = CopyOnWriteArrayList<Boolean>()
        client.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && event.packageName == packageName) {
                sensitivity += event.isAccessibilityDataSensitive
            }
        }
        instrumentation.runOnMainSync {
            secretText = CHANGED_SECRET
            Snapshot.sendApplyNotifications()
        }
        SystemClock.sleep(EVENT_WINDOW.inWholeMilliseconds)
        client.setOnAccessibilityEventListener(null)
        return sensitivity.toList()
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
                .height(TARGET_HEIGHT)
                .onGloballyPositioned { coordinates ->
                    target.localView = localView
                    target.centre = coordinates.boundsInWindow().center
                }
                .clickable(onClick = onClick)
        )
    }

    private fun awaitPlaced(target: Target) {
        var lastCentre: Offset? = null
        awaitCondition({ "the click target never settled: centre=${target.centre} attached=${target.localView?.isAttachedToWindow}" }) {
            val centre = target.centre
            val isSettled = centre != null && centre == lastCentre && target.localView?.isAttachedToWindow == true
            lastCentre = centre
            isSettled
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        awaitCondition({ message }, condition)
    }

    private fun awaitCondition(message: () -> String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT.inWholeMilliseconds
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL.inWholeMilliseconds)
        }
        fail(message())
    }

    private fun collectTexts(node: AccessibilityNodeInfo): List<String> {
        val own = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        val children = (0 until node.childCount).mapNotNull(node::getChild).flatMap(::collectTexts)
        return own + children
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
        val pointers = arrayOf(properties)
        val metaState = 0
        val buttonState = 0
        val xPrecision = 1f
        val yPrecision = 1f
        val deviceId = 0
        val edgeFlags = 0
        return MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            pointers.size,
            pointers,
            arrayOf(coords),
            metaState,
            buttonState,
            xPrecision,
            yPrecision,
            deviceId,
            edgeFlags,
            InputDevice.SOURCE_TOUCHSCREEN,
            flags,
        )
    }

    class Target {
        var localView: View? = null
        var centre: Offset? = null
    }

    companion object {
        // @hide MotionEvent.FLAG_IS_ACCESSIBILITY_EVENT and FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL
        const val FLAG_IS_ACCESSIBILITY_EVENT = 0x800
        const val FLAG_INJECTED_FROM_ACCESSIBILITY_TOOL = 0x1000

        // @TestApi UiAutomation.FLAG_NOT_ACCESSIBILITY_TOOL
        private const val FLAG_NOT_ACCESSIBILITY_TOOL = 0x4
        private const val TOOL_CLIENT_FLAGS = 0

        const val SECRET = "harbor velvet"
        const val VISIBLE_TEXT = "Recovery phrase"
        private const val CHANGED_SECRET = "meadow signal"
        private val TARGET_HEIGHT = 120.dp
        private val SETTLE_TIMEOUT = 30.seconds
        private val POLL_INTERVAL = 50.milliseconds
        private val CONNECTION_SETTLE = 3.seconds
        private val EVENT_WINDOW = 4.seconds
    }
}
