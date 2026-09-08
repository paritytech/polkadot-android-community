package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket

import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings.VoucherBarLayout
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.holdings.voucherBarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val COLUMN = 300f
private const val MIN_WIDTH = 21f
private const val GAP = 4f
private const val TOLERANCE = 0.001f

class VoucherBarLayoutTest {
    @Test
    fun `with room to spare both marks keep their nominal lengths`() {
        val layout = layoutOf(solidEnd = 0.2f, poleEnd = 0.6f)

        assertEquals(60f, layout.solidWidth, TOLERANCE)
        assertEquals(64f, layout.poleLeft, TOLERANCE)
        assertEquals(120f, layout.poleWidth, TOLERANCE)
    }

    @Test
    fun `a score that rounds away still leaves a mark of the minimum width`() {
        val layout = layoutOf(solidEnd = 0f, poleEnd = 0f)

        assertEquals(MIN_WIDTH, layout.solidWidth, TOLERANCE)
        assertEquals(MIN_WIDTH, layout.poleWidth, TOLERANCE)
    }

    /**
     * The common case rather than an extreme: a voucher whose maximum has not been frozen scores zero, so the
     * solid bar wants the whole column and the pole has nowhere to sit.
     */
    @Test
    fun `a full length solid bar gives up room so the pole stays on screen`() {
        val layout = layoutOf(solidEnd = 1f, poleEnd = 1f)

        assertEquals("pole is pinned to the right edge", COLUMN, layout.poleLeft + layout.poleWidth, TOLERANCE)
        assertEquals(MIN_WIDTH, layout.poleWidth, TOLERANCE)
        assertEquals(COLUMN - MIN_WIDTH - GAP, layout.solidWidth, TOLERANCE)
    }

    @Test
    fun `neither mark ever disappears across the whole range`() {
        val steps = 20

        for (solidStep in 0..steps) {
            for (poleStep in solidStep..steps) {
                val solidEnd = solidStep / steps.toFloat()
                val poleEnd = poleStep / steps.toFloat()
                val layout = layoutOf(solidEnd, poleEnd)

                assertTrue("solid vanished at $solidEnd/$poleEnd", layout.solidWidth > 0f)
                assertTrue("pole vanished at $solidEnd/$poleEnd", layout.poleWidth > 0f)
                assertTrue("pole starts before the column", layout.poleLeft >= 0f)
                assertTrue(
                    "pole ends past the column",
                    layout.poleLeft + layout.poleWidth <= COLUMN + TOLERANCE
                )
                assertTrue(
                    "marks overlap",
                    layout.solidWidth <= layout.poleLeft - GAP + TOLERANCE
                )
            }
        }
    }

    private fun layoutOf(solidEnd: Float, poleEnd: Float): VoucherBarLayout = voucherBarLayout(
        columnWidth = COLUMN,
        minWidth = MIN_WIDTH,
        gap = GAP,
        solidEnd = solidEnd,
        poleEnd = poleEnd
    )
}
