package io.paritytech.polkadotapp.feature_videogame_impl.utils

import android.Manifest
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.utils.openAppSettings
import io.paritytech.polkadotapp.common.utils.permissions.PermissionAsker
import io.paritytech.polkadotapp.common.utils.permissions.PermissionResult
import io.paritytech.polkadotapp.feature_videogame_impl.VideoGameRouter
import io.paritytech.polkadotapp.feature_videogame_impl.service.VideoGameServiceController
import io.paritytech.polkadotapp.feature_videogame_impl.service.VideoGameStateReader
import javax.inject.Inject

class VideoGameLaunchCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val router: VideoGameRouter,
    private val permissionAsker: PermissionAsker,
    private val serviceController: VideoGameServiceController,
    private val stateReader: VideoGameStateReader,
) {
    suspend fun launchGame() {
        when (permissionAsker.askPermission(Manifest.permission.CAMERA)) {
            PermissionResult.GRANTED -> {
                serviceController.start()
                router.openGamePlay()
            }

            PermissionResult.DENIED,
            PermissionResult.DENIED_FOREVER -> {
                appContext.openAppSettings()
            }
        }
    }

    /**
     * Re-enter a running session, or run the full launch flow if there is none.
     * Re-entry skips permission/service-start so the in-game pill tap remains instant.
     */
    suspend fun openOrLaunchGame() {
        if (stateReader.isSessionRunning) {
            router.openGamePlay()
        } else {
            launchGame()
        }
    }
}
