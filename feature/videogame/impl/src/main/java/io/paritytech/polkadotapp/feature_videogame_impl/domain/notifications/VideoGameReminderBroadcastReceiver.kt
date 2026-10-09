package io.paritytech.polkadotapp.feature_videogame_impl.domain.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.common.presentation.AppLifecycleObserver
import io.paritytech.polkadotapp.feature_videogame_impl.VideoGameNotificationPublisher
import javax.inject.Inject

@AndroidEntryPoint
class VideoGameReminderBroadcastReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_POST_NOTIFICATION = "io.paritytech.polkadotapp.feature_videogame.domain.notifications.POST_NOTIFICATION"
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
    }

    @Inject
    lateinit var notificationPublisher: VideoGameNotificationPublisher

    @Inject
    lateinit var appLifecycleObserver: AppLifecycleObserver

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_POST_NOTIFICATION -> {
                when (intent.getParcelableExtra<VideoGameNotificationType>(EXTRA_NOTIFICATION_TYPE)) {
                    is VideoGameNotificationType.ProductGameStartsSoon,
                    null -> Unit
                }
            }
        }
    }
}
