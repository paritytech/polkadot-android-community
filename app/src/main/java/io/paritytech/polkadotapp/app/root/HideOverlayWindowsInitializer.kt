package io.paritytech.polkadotapp.app.root

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.AppInitializer
import javax.inject.Inject

class HideOverlayWindowsInitializer @Inject constructor(
    private val application: Application,
) : AppInitializer {
    context(scope: ComputationalScope)
    override fun initialize(): Result<Unit> = runCatching {
        application.registerActivityLifecycleCallbacks(HideOverlayWindowsCallbacks)
    }
}

private object HideOverlayWindowsCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.window.setHideOverlayWindows(true)
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
