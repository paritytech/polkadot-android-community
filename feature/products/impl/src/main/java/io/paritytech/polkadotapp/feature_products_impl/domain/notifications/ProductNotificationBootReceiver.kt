package io.paritytech.polkadotapp.feature_products_impl.domain.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.common.utils.launchAsyncJob
import javax.inject.Inject

@AndroidEntryPoint
class ProductNotificationBootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var scheduler: ProductNotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        launchAsyncJob {
            scheduler.restoreAll()
        }
    }
}
