package io.paritytech.polkadotapp.feature_wallet_impl.deeplink

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoSet
import io.paritytech.polkadotapp.common.presentation.deeplink.DeepLinkHandler
import io.paritytech.polkadotapp.feature_scan_api.domain.DeeplinkScanContentParser
import io.paritytech.polkadotapp.feature_scan_api.domain.ScanContentParser

@Module
@InstallIn(ViewModelComponent::class)
internal interface PayDeeplinkModule {
    @Binds
    @IntoSet
    fun bindPayDeepLinkHandler(impl: PayDeepLinkHandler): DeepLinkHandler

    companion object {
        @Provides
        @IntoSet
        fun providePayScanContentParser(
            handler: PayDeepLinkHandler,
        ): ScanContentParser = DeeplinkScanContentParser(handler)
    }
}
