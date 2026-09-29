package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.coins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The parts of the studio's turn that are arithmetic rather than sensor plumbing.
 *
 * The symmetry test is the one that matters: a light that answered one direction of tilt and not the other
 * is the bug the roll-about-the-view-axis choice exists to fix.
 */
class CoinageTiltTest {
    @Test
    fun `a roll turns the studio about the view axis, symmetrically`() {
        val left = CoinageTilt.composed(pitch = 0.0, roll = CoinageTilt.scaled(-EIGHTH_TURN))
        val right = CoinageTilt.composed(pitch = 0.0, roll = CoinageTilt.scaled(EIGHTH_TURN))

        assertEquals("roll is about z alone", 0.0, abs(left.x) + abs(left.y), TOLERANCE)
        assertEquals("roll is about z alone", 0.0, abs(right.x) + abs(right.y), TOLERANCE)
        assertEquals("equal and opposite", left.z, -right.z, TOLERANCE)
        assertTrue("a roll turns the studio at all", abs(left.z) > 0.1)
    }

    @Test
    fun `a lean pitches the studio about x`() {
        val turn = CoinageTilt.composed(pitch = CoinageTilt.scaled(EIGHTH_TURN), roll = 0.0)

        assertEquals("pitch is about x alone", 0.0, abs(turn.y) + abs(turn.z), TOLERANCE)
        assertTrue("a lean turns the studio at all", abs(turn.x) > 0.1)
    }

    @Test
    fun `the two turns compose as rotations rather than adding`() {
        val pitch = CoinageTilt.scaled(EIGHTH_TURN)
        val roll = CoinageTilt.scaled(EIGHTH_TURN)
        val turn = CoinageTilt.composed(pitch, roll)

        // Added, the y component would be zero and the length would be exactly the hypotenuse of the two.
        assertTrue("the cross term is real", abs(turn.y) > TOLERANCE)
        assertTrue(
            "composed is not the sum",
            abs(sqrt(turn.x * turn.x + turn.y * turn.y + turn.z * turn.z) - hypot(pitch, roll)) > TOLERANCE
        )
    }

    @Test
    fun `travel is clamped past the end of the range`() {
        assertEquals(CoinageTilt.TRAVEL, CoinageTilt.scaled(CoinageTilt.RANGE), TOLERANCE)
        assertEquals(CoinageTilt.TRAVEL, CoinageTilt.scaled(PI), TOLERANCE)
        assertEquals(-CoinageTilt.TRAVEL, CoinageTilt.scaled(-PI), TOLERANCE)
    }

    /**
     * A bearing between two components that both shrink is ill-conditioned exactly where the phone is held:
     * leaned back. Each angle is taken against everything left over instead, so a five degree roll reads as
     * five degrees however far back the phone is leaned.
     */
    @Test
    fun `a roll reads the same however far back the phone is leaned`() {
        val readings = listOf(0.0, 0.5, 1.0, 1.4).map { lean ->
            val upright = pose(roll = 0.0, lean = lean)

            pose(roll = FIVE_DEGREES, lean = lean).sideways - upright.sideways
        }

        for (reading in readings) {
            assertEquals("roll should read five degrees", FIVE_DEGREES, reading, 1e-6)
        }
    }

    /** Gravity in the phone's frame for a device rolled and leaned by the given angles. */
    private fun pose(roll: Double, lean: Double): CoinageTilt.Pose {
        // Down, in the device frame: upright portrait is -y, leaning back tips it toward -z, rolling right
        // tips it toward +x.
        val x = kotlin.math.sin(roll)
        val flat = kotlin.math.cos(roll)

        return CoinageTilt.Pose.of(x, -flat * kotlin.math.cos(lean), -flat * kotlin.math.sin(lean))
    }

    private fun hypot(a: Double, b: Double) = sqrt(a * a + b * b)

    private companion object {
        const val TOLERANCE = 1e-9
        const val EIGHTH_TURN = PI / 4
        const val FIVE_DEGREES = 5 * PI / 180
    }
}
