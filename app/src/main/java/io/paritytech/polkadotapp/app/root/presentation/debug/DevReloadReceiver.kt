package io.paritytech.polkadotapp.app.root.presentation.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.feature_products_api.domain.browser.ProductSessionController
import timber.log.Timber
import javax.inject.Inject

private const val ACTION_DEV_RELOAD = "io.paritytech.polkadotapp.DEV_RELOAD"

/**
 * Reloads the active product tab on `adb shell am broadcast -a io.paritytech.polkadotapp.DEV_RELOAD`,
 * so a rebuild on the developer machine lands on the device without touching it.
 */
class DevReloadReceiver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    // Deferred so registering the receiver at process start does not build the tab session graph.
    private val productSessionController: Lazy<ProductSessionController>,
) : BroadcastReceiver() {
    /**
     * Exported because `am broadcast` arrives from outside the app. It carries no payload and the only
     * effect is a page refresh, and it is registered on a developer build alone — never in the manifest.
     */
    fun register() {
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(ACTION_DEV_RELOAD),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("Dev reload broadcast received")

        productSessionController.get().reload()
    }
}
