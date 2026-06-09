package io.paritytech.polkadotapp.feature_videogame_impl.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.notifications.PolkadotNotificationChannel
import io.paritytech.polkadotapp.feature_videogame_impl.domain.models.VideoGameProcessState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import io.paritytech.polkadotapp.common.R as RCommon

@AndroidEntryPoint
class VideoGameService : Service(), ComputationalScope {
    companion object {
        private const val ACTION_START_GAME = "io.paritytech.polkadotapp.action.START_VIDEO_GAME"
        private const val ACTION_STOP_GAME = "io.paritytech.polkadotapp.action.STOP_VIDEO_GAME"

        private const val NOTIFICATION_ID = 998

        fun startGameIntent(context: Context): Intent {
            return Intent(context, VideoGameService::class.java)
                .setAction(ACTION_START_GAME)
        }

        fun stopGameIntent(context: Context): Intent {
            return Intent(context, VideoGameService::class.java)
                .setAction(ACTION_STOP_GAME)
        }
    }

    override val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()

    @Inject
    lateinit var sessionManager: VideoGameSessionManager

    @Inject
    lateinit var stateHolder: VideoGameStateHolder

    private var sessionRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_GAME -> startGameIfNotRunning()
            ACTION_STOP_GAME -> stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startGameIfNotRunning() {
        if (sessionRunning) return
        sessionRunning = true

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        )

        launch {
            try {
                stateHolder.setSessionManager(sessionManager)
                sessionManager.startSession()
                awaitGameSessionFinished()
            } finally {
                sessionManager.endSession()
                sessionRunning = false
            }

            stateHolder.endSession()
            stopSelf()
        }
    }

    private suspend fun awaitGameSessionFinished() {
        stateHolder.gameSnapshot
            .filterNotNull()
            .map { it.processState }
            .first { it is VideoGameProcessState.Reporting || it is VideoGameProcessState.Finished || it is VideoGameProcessState.Error }
    }

    override fun onDestroy() {
        cancel()
        stateHolder.endSession()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channel = PolkadotNotificationChannel.VIDEO_GAME
        ensureNotificationChannel(channel)

        return NotificationCompat.Builder(this, channel.id)
            .setSmallIcon(RCommon.drawable.ic_notification_default)
            .setOngoing(true)
            .setSilent(true)
            .setContentTitle(getString(RCommon.string.video_game_service_notification_title))
            .build()
    }

    private fun ensureNotificationChannel(channel: PolkadotNotificationChannel) {
        val notificationManager = NotificationManagerCompat.from(this)
        val notificationChannel = NotificationChannelCompat.Builder(channel.id, channel.importance)
            .setName(getString(channel.nameRes))
            .setDescription(getString(channel.descriptionRes))
            .build()

        notificationManager.createNotificationChannel(notificationChannel)
    }
}
