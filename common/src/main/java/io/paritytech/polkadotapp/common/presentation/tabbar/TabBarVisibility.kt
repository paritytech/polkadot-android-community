package io.paritytech.polkadotapp.common.presentation.tabbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the bar is hidden entirely (no nub, not pullable) or force-shown. A screen hides it via
 * [HideTabBar] or pins it via [ForceShowTabBar]; with neither, the bar shows its nub and can be pulled out
 * (the default, e.g. on Main). The bar stays hidden for the whole intro flow: it does not exist until the
 * first screen that force-shows it (Main) appears, after which the per-screen keys take over.
 */
@Singleton
class TabBarVisibilityHolder @Inject constructor() {
    private val hideKeys = mutableSetOf<Any>()
    private val forceKeys = mutableSetOf<Any>()

    private var introGateOpen = false

    val hidden: StateFlow<Boolean>
        field = MutableStateFlow(true)

    // Whether some screen forces the bar shown (e.g. Main) — it can't be hidden and its outside-tap
    // scrim is disabled so content stays clickable.
    val forced: StateFlow<Boolean>
        field = MutableStateFlow(false)

    @Synchronized
    fun hide(key: Any) {
        hideKeys.add(key)
        recompute()
    }

    @Synchronized
    fun show(key: Any) {
        hideKeys.remove(key)
        recompute()
    }

    @Synchronized
    fun forceShow(key: Any) {
        forceKeys.add(key)
        introGateOpen = true
        recompute()
    }

    @Synchronized
    fun releaseForce(key: Any) {
        forceKeys.remove(key)
        recompute()
    }

    private fun recompute() {
        forced.value = forceKeys.isNotEmpty()
        hidden.value = !introGateOpen || (hideKeys.isNotEmpty() && forceKeys.isEmpty())
    }
}

val LocalTabBarVisibility = staticCompositionLocalOf<TabBarVisibilityHolder?> { null }

/** Call inside a screen's composable to hide the global navigation bar entirely while it is present. */
@Composable
fun HideTabBar() {
    val holder = LocalTabBarVisibility.current ?: return
    DisposableEffect(holder) {
        val key = Any()
        holder.hide(key)
        onDispose { holder.show(key) }
    }
}

/**
 * Call inside a screen's composable to force the bar shown while it is present — it can't be hidden, and
 * outside taps pass through to content (used on Main).
 */
@Composable
fun ForceShowTabBar() {
    val holder = LocalTabBarVisibility.current ?: return
    DisposableEffect(holder) {
        val key = Any()
        holder.forceShow(key)
        onDispose { holder.releaseForce(key) }
    }
}
