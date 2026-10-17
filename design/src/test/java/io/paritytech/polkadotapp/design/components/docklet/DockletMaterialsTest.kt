package io.paritytech.polkadotapp.design.components.docklet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockletMaterialsTest {
    @Test
    fun `backdrop blur is on when the API level is 31`() {
        assertTrue(DockletMaterials.backdropBlurSupported(31))
    }

    @Test
    fun `backdrop blur is off when the API level is 30`() {
        assertFalse(DockletMaterials.backdropBlurSupported(30))
    }

    @Test
    fun `blur radius is 113 45 px when sigma is 24 dp at density 2 75`() {
        assertEquals(113.45f, DockletMaterials.blurRadiusPx(24f, 2.75f), EPS)
    }

    @Test
    fun `blur radius is 46 77 px when sigma is 10 dp at density 2 75`() {
        assertEquals(46.77f, DockletMaterials.blurRadiusPx(10f, 2.75f), EPS)
    }

    @Test
    fun `blur radius is 37 24 px when sigma is 8 dp at density 2 75`() {
        assertEquals(37.24f, DockletMaterials.blurRadiusPx(8f, 2.75f), EPS)
    }

    @Test
    fun `blur radius is 13 42 px when sigma is 3 dp at density 2 75`() {
        assertEquals(13.42f, DockletMaterials.blurRadiusPx(3f, 2.75f), EPS)
    }

    @Test
    fun `blur radius is 12 77 px when sigma is 3 dp at density 2 625`() {
        assertEquals(12.77f, DockletMaterials.blurRadiusPx(3f, 2.625f), EPS)
    }

    private companion object {
        const val EPS = 0.01f
    }
}
