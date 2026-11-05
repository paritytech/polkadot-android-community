package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.coins

import android.os.Build
import android.view.Choreographer
import androidx.annotation.RequiresApi

/**
 * Debug only: how long a gravity reading takes to reach the glass, carried through the pipeline as one
 * timestamp and read back from the frame timeline. All times are on the monotonic clock, in nanoseconds.
 *
 * Stages: the sensor to the app, the app to the draw that first uses the reading, that draw to its swap
 * returning, the swap to the UI frame latching the texture, and that frame to its expected presentation.
 * Its own vsync callback draws nothing; when it runs no faster than the draws, the device is capped, not the
 * renderer.
 *
 * Lives on the main thread.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class CoinageLatencyProbe(private val onReport: (String) -> Unit) {
    class Sample(val sensed: Long, val delivered: Long)

    class Frame(val sequence: Long, val sample: Sample, val drawn: Long, val swapped: Long)

    private val choreographer = Choreographer.getInstance()
    private val stages = Array(STAGES) { LongArray(WINDOW) }
    private val totals = LongArray(WINDOW)
    private var count = 0
    private var next = 0
    private var lastSequence = -1L
    private var lastReport = 0L
    private var lastVsync = 0L
    private var vsyncInterval = 0.0
    private var presentAt = 0L
    private var running = false

    private val callback = object : Choreographer.VsyncCallback {
        override fun onVsync(data: Choreographer.FrameData) {
            if (!running) return

            val now = data.frameTimeNanos

            if (lastVsync != 0L) vsyncInterval = smoothed(vsyncInterval, (now - lastVsync).toDouble())
            lastVsync = now
            presentAt = data.preferredFrameTimeline.expectedPresentationTimeNanos
            choreographer.postVsyncCallback(this)
        }
    }

    fun start() {
        if (running) return

        running = true
        lastVsync = 0L
        choreographer.postVsyncCallback(callback)
    }

    fun stop() {
        running = false
        choreographer.removeVsyncCallback(callback)
    }

    /** Called as the UI frame latches the texture; [frame] is the last stamped frame swapped. */
    fun latched(frame: Frame?, drawInterval: Double) {
        if (frame == null || frame.sequence == lastSequence) return

        lastSequence = frame.sequence

        val latch = System.nanoTime()
        val present = presentAt

        // Never shown, or a timeline from an earlier frame: either reads as a huge or negative latency.
        if (present <= 0L || present < latch) return

        val sample = frame.sample
        val spans = longArrayOf(
            sample.delivered - sample.sensed,
            frame.drawn - sample.delivered,
            frame.swapped - frame.drawn,
            latch - frame.swapped,
            present - latch
        )

        spans.forEachIndexed { stage, span -> stages[stage][next] = span }
        totals[next] = present - sample.sensed
        next = (next + 1) % WINDOW
        count = minOf(count + 1, WINDOW)

        if (latch - lastReport >= REPORT_EVERY) {
            lastReport = latch
            onReport(report(drawInterval))
        }
    }

    private fun report(drawInterval: Double): String {
        val sorted = totals.copyOf(count).sorted()
        val lines = NAMES.mapIndexed { stage, name ->
            "%-12s %5.1f".format(name, stages[stage].take(count).average() / MILLIS)
        }

        return (
            lines + listOf(
                "total  avg %.1f  p90 %.1f  max %.1f".format(
                    sorted.average() / MILLIS,
                    sorted[(count - 1) * 9 / 10] / MILLIS,
                    sorted.last() / MILLIS
                ),
                "draw %.1f ms  vsync %.1f ms  n=%d".format(drawInterval / MILLIS, vsyncInterval / MILLIS, count)
            )
            ).joinToString("\n")
    }

    companion object {
        private const val STAGES = 5
        private const val WINDOW = 240
        private const val REPORT_EVERY = 250_000_000L
        private const val MILLIS = 1_000_000.0
        private val NAMES = listOf("sensor>app", "app>draw", "draw>swap", "swap>latch", "latch>glass")

        /** A running mean over roughly the last thirty intervals. */
        fun smoothed(average: Double, sample: Double) = if (average == 0.0) sample else average + (sample - average) / 30
    }
}
