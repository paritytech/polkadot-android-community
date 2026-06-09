package io.paritytech.polkadotapp.app.root.deeplink.videogame

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoSet
import io.paritytech.polkadotapp.common.presentation.deeplink.DeepLinkHandler

@Module
@InstallIn(ViewModelComponent::class)
internal interface VideoGameDeeplinkModule {
    @Binds
    @IntoSet
    fun bindVideoGameDeepLinkHandler(impl: VideoGameDeepLinkHandler): DeepLinkHandler
}
