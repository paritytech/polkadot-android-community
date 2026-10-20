package io.paritytech.polkadotapp.common.presentation.theme

import io.paritytech.polkadotapp.common.data.storage.preferences.Preferences
import io.paritytech.polkadotapp.designsystem.themes.PolkadotAppTheme
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

private const val THEME_KEY = "selected_app_theme_id"

class RealAppThemeSelectorTest {
    private val preferences: Preferences = mock(Preferences::class.java)

    @Test
    fun `current theme is the stored theme when a theme id is stored`() {
        withStoredThemeId(PolkadotAppTheme.Tokyo.id)

        assertEquals(PolkadotAppTheme.Tokyo, RealAppThemeSelector(preferences).currentTheme)
    }

    @Test
    fun `current theme is the default when no theme id is stored`() {
        withStoredThemeId(null)

        assertEquals(PolkadotAppTheme.DEFAULT, RealAppThemeSelector(preferences).currentTheme)
    }

    @Test
    fun `current theme is the default when the stored id is unknown`() {
        withStoredThemeId("removed-theme")

        assertEquals(PolkadotAppTheme.DEFAULT, RealAppThemeSelector(preferences).currentTheme)
    }

    @Test
    fun `current theme matches the first emitted theme when any theme is stored`() = runBlocking<Unit> {
        PolkadotAppTheme.entries.forEach { theme ->
            withStoredThemeId(theme.id)
            val selector = RealAppThemeSelector(preferences)

            assertEquals(selector.selectedTheme.first(), selector.currentTheme)
        }
    }

    private fun withStoredThemeId(id: String?) {
        whenever(preferences.getString(THEME_KEY)).thenReturn(id)
        whenever(preferences.stringFlow(THEME_KEY)).thenReturn(flowOf(id))
    }
}
