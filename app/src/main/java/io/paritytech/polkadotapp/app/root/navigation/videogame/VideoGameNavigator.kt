package io.paritytech.polkadotapp.app.root.navigation.videogame

import io.paritytech.polkadotapp.app.root.navigation.BaseNavigator
import io.paritytech.polkadotapp.app.root.navigation.NavigationHolder
import io.paritytech.polkadotapp.feature_videogame_impl.VideoGameRouter
import jakarta.inject.Inject

class VideoGameNavigator @Inject constructor(
    navigationHolder: NavigationHolder,
) : BaseNavigator(navigationHolder), VideoGameRouter
