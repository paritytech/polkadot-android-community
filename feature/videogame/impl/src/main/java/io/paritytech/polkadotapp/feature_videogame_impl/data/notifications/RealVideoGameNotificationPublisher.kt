package io.paritytech.polkadotapp.feature_videogame_impl.data.notifications

import android.app.Notification
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.presentation.ActivityIntentProvider
import io.paritytech.polkadotapp.common.presentation.notifications.NotificationPublisher
import io.paritytech.polkadotapp.feature_videogame_impl.VideoGameNotificationPublisher
import io.paritytech.polkadotapp.feature_videogame_impl.deeplink.VideoGameDeeplinkMapper
import javax.inject.Inject

class RealVideoGameNotificationPublisher @Inject constructor(
    @ApplicationContext context: Context,
    intentProvider: ActivityIntentProvider,
    private val videoGameDeeplinkMapper: VideoGameDeeplinkMapper
) : NotificationPublisher(context, intentProvider), VideoGameNotificationPublisher {
    private fun Notification.applyAlarmFlags(): Notification {
        flags = flags or Notification.FLAG_INSISTENT
        return this
    }
}
