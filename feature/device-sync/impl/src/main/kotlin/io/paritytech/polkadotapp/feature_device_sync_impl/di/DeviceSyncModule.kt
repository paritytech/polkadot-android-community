package io.paritytech.polkadotapp.feature_device_sync_impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.paritytech.polkadotapp.common.presentation.AppInitializer
import io.paritytech.polkadotapp.feature_device_sync_api.domain.DeviceSyncCoordinator
import io.paritytech.polkadotapp.feature_device_sync_api.domain.SiblingFileFetcher
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.RealDeviceSyncCoordinator
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.RealSiblingFileFetcher
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.RealSyncFileLocator
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.fileTransfer.SyncFileLocator
import io.paritytech.polkadotapp.feature_device_sync_impl.presentation.initialization.DeviceSyncInitializer

@Module
@InstallIn(SingletonComponent::class)
internal interface DeviceSyncModule {
    @Binds
    fun bindDeviceSyncCoordinator(impl: RealDeviceSyncCoordinator): DeviceSyncCoordinator

    @Binds
    fun bindSiblingFileFetcher(impl: RealSiblingFileFetcher): SiblingFileFetcher

    @Binds
    fun bindSyncFileLocator(impl: RealSyncFileLocator): SyncFileLocator

    @Binds
    @IntoSet
    fun bindInitializer(impl: DeviceSyncInitializer): AppInitializer
}
