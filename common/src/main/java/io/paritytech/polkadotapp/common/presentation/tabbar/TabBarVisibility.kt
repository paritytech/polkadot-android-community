package io.paritytech.polkadotapp.common.presentation.tabbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the bar is shown at all. It exists only on screens that pin it via [ForceShowTabBar] (Main);
 * everywhere else it is absent entirely — no nub, nothing to pull out. There is deliberately no opt-out
 * state in between, so a screen cannot end up with a bar it did not ask for.
 */
@Singleton
class TabBarVisibilityHolder @Inject constructor() {
    private val forceKeys = mutableSetOf<Any>()

    val hidden: StateFlow<Boolean>
        field = MutableStateFlow(true)

    // Whether some screen forces the bar shown (e.g. Main) — its outside-tap scrim is disabled so content
    // stays clickable.
    val forced: StateFlow<Boolean>
        field = MutableStateFlow(false)

    @Synchronized
    fun forceShow(key: Any) {
        forceKeys.add(key)
        recompute()
    }

    @Synchronized
    fun releaseForce(key: Any) {
        forceKeys.remove(key)
        recompute()
    }

    private fun recompute() {
        forced.value = forceKeys.isNotEmpty()
        hidden.value = forceKeys.isEmpty()
    }
}

val LocalTabBarVisibility = staticCompositionLocalOf<TabBarVisibilityHolder?> { null }

/**
 * Call inside a screen's composable to show the bar from the moment the host lifecycle resumes until the
 * composition leaves; a screen a predictive-back gesture is only previewing never resumes, so it gets no
 * bar. Outside taps pass through to content (used on Main). Screens that never call this have no bar.
 */
@Composable
fun ForceShowTabBar() {
    val holder = LocalTabBarVisibility.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    if (holder != null) {
        LaunchedEffect(holder, lifecycle) {
            // A predictive-back gesture composes the screen it previews without ever resuming it. Waiting is
            // one-shot because the composition outlives ON_STOP — releasing on pause republishes a zero bar height.
            lifecycle.withResumed {}
            val key = Any()
            holder.forceShow(key)
            try {
                awaitCancellation()
            } finally {
                holder.releaseForce(key)
            }
        }
    }
}
