package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.animation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// Normalized device tilt for the holographic card, ported from the designer's GyroShineTest.
// x: -1 (left) .. 0 .. 1 (right) — from roll.
// y:  0 .. 1 (neutral hold) .. 2 — from pitch (1 is the rest pose, so layers sit centered at entry).
@Immutable
data class TiltState(val x: Float, val y: Float)

// Roll/pitch deltas (radians) that map to the full ±1 (x) / 0..2 (y) range; beyond, the tilt clamps. Pitch
// is a tighter span than roll (designer values); pitch is measured as a delta from PITCH_NEUTRAL_RAD.
private const val MAX_ROLL_DELTA_RAD = 0.58f
private const val MAX_PITCH_DELTA_RAD = 0.28f

// Neutral pitch (radians) = the rest pose where tilt.y == 1. The designer's "0" is the phone held at ~45°
// from flat (a natural mid hold), not lying flat. Sign is device-frame dependent — flip if the vertical
// response is inverted on device.
private const val PITCH_NEUTRAL_RAD = -0.785f

// Per-event lerp toward the latest sensor reading; lower = heavier/smoother.
private const val SMOOTHING = 0.28f

// Accelerometer fallback divisor (m/s² -> normalized) when no rotation-vector sensor exists.
private const val ACCEL_NORMALIZER = 8f

// Static neutral tilt, used when no provider is present (e.g. @Preview) so cards still render.
private val ZeroTilt: State<TiltState> = mutableStateOf(TiltState(0f, 1f))

// Screen-scoped device tilt, shared by the list + details member cards through composition. PocketScreen
// provides the single live source so both cards read the same tilt and stay continuous across the
// shared-element transition. Read it inside graphicsLayer { } to keep updates off the recomposition path.
val LocalCardTilt = staticCompositionLocalOf { ZeroTilt }

// Creates the live tilt source, owned by the calling composition. Reads absolute device orientation from the
// fused game-rotation-vector sensor (gyro + accelerometer), normalizes roll/pitch into the TiltState range
// and smooths it. Falls back to ROTATION_VECTOR, then raw ACCELEROMETER.
@Composable
fun rememberCardTilt(): State<TiltState> {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val source = remember { TiltSource() }

    // Sensor lives with the Pocket screen's composition: MainScreen swaps tabs via Crossfade, so this
    // composable is present only while the WALLET tab is selected. Start on enter / stop on leave ties the
    // sensor to that visibility; the lifecycle observer only pauses it while the app is backgrounded.
    DisposableEffect(lifecycleOwner, source) {
        source.start(context)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> source.start(context)
                Lifecycle.Event.ON_STOP -> source.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            source.stop()
        }
    }

    return source.tilt
}

// One owner per composition (no ref-count): the sensor is registered while the screen is started and
// unregistered when it stops or the holder leaves composition.
private class TiltSource {
    val tilt = mutableStateOf(TiltState(0f, 1f))

    // Smoothed on the sensor thread; tilt state is a snapshot write so reads stay consistent.
    private var smoothedX = 0f
    private var smoothedY = 1f

    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun start(context: Context) {
        if (listener != null) return

        val manager = context.getSystemService<SensorManager>() ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return
        sensorManager = manager

        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onSensorEvent(event)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        sensorManager = null
        listener = null
    }

    private fun onSensorEvent(event: SensorEvent) {
        var targetX = 0f
        var normalizedY = 0f

        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val pitch = orientationAngles[1]
                val roll = orientationAngles[2]
                targetX = (roll / MAX_ROLL_DELTA_RAD).coerceIn(-1f, 1f)
                normalizedY = (-(pitch - PITCH_NEUTRAL_RAD) / MAX_PITCH_DELTA_RAD).coerceIn(-1f, 1f)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                targetX = (-ax / ACCEL_NORMALIZER).coerceIn(-1f, 1f)
                normalizedY = (ay / ACCEL_NORMALIZER).coerceIn(-1f, 1f)
            }
        }

        val targetY = 1f + normalizedY

        smoothedX += (targetX - smoothedX) * SMOOTHING
        smoothedY += (targetY - smoothedY) * SMOOTHING

        tilt.value = TiltState(
            x = smoothedX.coerceIn(-1f, 1f),
            y = smoothedY.coerceIn(0f, 2f)
        )
    }
}
