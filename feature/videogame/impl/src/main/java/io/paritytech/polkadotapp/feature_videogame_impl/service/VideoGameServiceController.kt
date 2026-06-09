package io.paritytech.polkadotapp.feature_videogame_impl.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class VideoGameServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun start() {
        context.startForegroundService(VideoGameService.startGameIntent(context))
    }

    fun stop() {
        context.startService(VideoGameService.stopGameIntent(context))
    }
}
