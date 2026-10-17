package io.paritytech.polkadotapp.design.components.docklet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class CardOutlineTest {
    @Test
    fun `outline equals the brief's step-1 path when the card is 382 by 204`() {
        val outline = DockletGeometry.cardOutline(width = 382f, height = 204f)

        assertEquals(32f, outline.cornerRadius, EPS)
        assertPoint(235f, 204f, outline.lobeStart)
        assertCurves(
            listOf(
                listOf(228f to 204f, 223f to 209f, 221f to 215f),
                listOf(217f to 227f, 204f to 235f, 191f to 235f),
                listOf(178f to 235f, 165f to 227f, 161f to 215f),
                listOf(159f to 209f, 154f to 204f, 147f to 204f),
            ),
            outline.lobe,
        )
    }

    @Test
    fun `corner radius caps at half the height when the body is 64 by 33`() {
        assertEquals(16.5f, DockletGeometry.cardOutline(width = 64f, height = 33f).cornerRadius, EPS)
    }

    @Test
    fun `corner radius caps at half the width when the body is 40 by 204`() {
        assertEquals(20f, DockletGeometry.cardOutline(width = 40f, height = 204f).cornerRadius, EPS)
    }

    @Test
    fun `lobe offsets shrink by w over 88 and round when the card is 80 dp wide`() {
        val outline = DockletGeometry.cardOutline(width = 80f, height = 204f)

        assertEquals(34f, offset(outline, outline.lobe[0].control1), EPS)
        assertEquals(27f, offset(outline, outline.lobe[0].end), EPS)
        assertEquals(0f, offset(outline, outline.lobe[1].end), EPS)
    }

    @Test
    fun `lobe uses k 0 727 when the card is 64 dp wide`() {
        val outline = DockletGeometry.cardOutline(width = 64f, height = 33f)
        val right = listOf(
            outline.lobeStart,
            outline.lobe[0].control1,
            outline.lobe[0].control2,
            outline.lobe[0].end,
            outline.lobe[1].control1,
            outline.lobe[1].control2,
        )

        assertEquals(listOf(32f, 27f, 23f, 22f, 19f, 9f), right.map { offset(outline, it) })
    }

    @Test
    fun `lobe keeps full offsets when the card is 100 dp wide`() {
        val outline = DockletGeometry.cardOutline(width = 100f, height = 204f)

        assertEquals(37f, offset(outline, outline.lobe[0].control1), EPS)
        assertEquals(30f, offset(outline, outline.lobe[0].end), EPS)
        assertEquals(44f, offset(outline, outline.lobeStart), EPS)
    }

    @Test
    fun `lobe rounds its offsets before adding the centre when the card is 73 dp wide`() {
        val outline = DockletGeometry.cardOutline(width = 73f, height = 204f)

        assertEquals(67.5f, outline.lobe[0].control1.x, EPS)
    }

    @Test
    fun `lobe stays 31 dp deep when the card is 64 dp wide`() {
        val outline = DockletGeometry.cardOutline(width = 64f, height = 33f)

        assertEquals(33f + 31f, outline.lobe[1].end.y, EPS)
    }

    @Test
    fun `outline is the 64 dp circle when the Pill is at rest`() {
        val outline = DockletGeometry.cardOutlineCurves(width = 64f, height = 33f, pillShare = 1f)

        val distances = samples(outline).map { hypot(it.x - 32f, it.y - 32f) }

        assertTrue("min ${distances.min()} max ${distances.max()}", distances.all { abs(it - 32f) < 0.05f })
    }

    @Test
    fun `outline follows the brief's step-1 path when the card is fully open`() {
        val outline = DockletGeometry.cardOutlineCurves(width = 382f, height = 204f, pillShare = 0f)
        val ends = outline.segments.map { it.end }

        assertPoint(32f, 0f, outline.start)
        assertTrue(ends.containsAll(listOf(DockletPoint(350f, 0f), DockletPoint(382f, 32f), DockletPoint(382f, 172f))))
        assertTrue(ends.containsAll(listOf(DockletPoint(235f, 204f), DockletPoint(191f, 235f), DockletPoint(147f, 204f))))
        assertEquals(DockletGeometry.cardOutline(382f, 204f).lobe, outline.segments.subList(5, 9))
    }

    private fun samples(outline: DockletOutline): List<DockletPoint> {
        var start = outline.start
        return outline.segments.flatMap { segment ->
            val from = start
            start = segment.end
            (0..SamplesPerSegment).map { index ->
                val t = index.toFloat() / SamplesPerSegment
                val u = 1f - t
                fun axis(p0: Float, p1: Float, p2: Float, p3: Float) =
                    u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
                DockletPoint(
                    axis(from.x, segment.control1.x, segment.control2.x, segment.end.x),
                    axis(from.y, segment.control1.y, segment.control2.y, segment.end.y),
                )
            }
        }
    }

    private fun offset(outline: DockletCardOutline, point: DockletPoint) = point.x - outline.width / 2

    private fun assertPoint(x: Float, y: Float, actual: DockletPoint) {
        assertEquals(x, actual.x, EPS)
        assertEquals(y, actual.y, EPS)
    }

    private fun assertCurves(expected: List<List<Pair<Float, Float>>>, actual: List<CubicSegment>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (points, segment) ->
            listOf(segment.control1, segment.control2, segment.end).zip(points).forEach { (point, expectedPoint) ->
                assertPoint(expectedPoint.first, expectedPoint.second, point)
            }
        }
    }

    private companion object {
        const val EPS = 1e-3f
        const val SamplesPerSegment = 50
    }
}
