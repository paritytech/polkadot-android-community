package io.paritytech.polkadotapp.feature_calls_impl.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import io.paritytech.polkadotapp.common.presentation.notifications.PolkadotNotificationChannel
import io.paritytech.polkadotapp.feature_calls_impl.models.CallParams
import io.paritytech.polkadotapp.feature_calls_impl.presentation.CallActivity
import io.paritytech.polkadotapp.feature_calls_impl.service.CallService
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import kotlin.random.Random
import io.paritytech.polkadotapp.common.R as RCommon

class CallNotificationManager(private val context: Context) {
    fun createIncomingCallNotification(callParams: CallParams): Notification {
        val channel = PolkadotNotificationChannel.CALLS
        ensureNotificationChannel(channel)

        val chatId = ChatId.fromRawValue(callParams.chatId)
        val person = Person.Builder().setName(callParams.callerName).build()

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            CallActivity.newIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pendingAnswerIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode() + 1,
            CallActivity.answerIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val pendingDeclineIntent = PendingIntent.getService(
            context,
            chatId.hashCode() + 2,
            CallService.declineCallIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callStyle = NotificationCompat.CallStyle.forIncomingCall(person, pendingDeclineIntent, pendingAnswerIntent)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(RCommon.drawable.ic_notification_default)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setStyle(callStyle)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        return notification
    }

    fun createOngoingCallNotification(callerName: String): Notification {
        val channel = PolkadotNotificationChannel.CALLS
        ensureNotificationChannel(channel)

        val person = Person.Builder().setName(callerName).build()

        val pendingHangUpIntent = PendingIntent.getService(
            context,
            Random.nextInt(),
            CallService.endCallIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pendingContentIntent = PendingIntent.getActivity(
            context,
            Random.nextInt(),
            CallActivity.newIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callStyle = NotificationCompat.CallStyle.forOngoingCall(person, pendingHangUpIntent)
        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(RCommon.drawable.ic_notification_default)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setStyle(callStyle)
            .setContentIntent(pendingContentIntent)
            .build()
    }

    private fun ensureNotificationChannel(channel: PolkadotNotificationChannel) {
        val notificationManager = NotificationManagerCompat.from(context)
        val notificationChannel = NotificationChannelCompat.Builder(channel.id, channel.importance)
            .setName(context.getString(channel.nameRes))
            .setDescription(context.getString(channel.descriptionRes))
            .build()

        notificationManager.createNotificationChannel(notificationChannel)
    }
}
