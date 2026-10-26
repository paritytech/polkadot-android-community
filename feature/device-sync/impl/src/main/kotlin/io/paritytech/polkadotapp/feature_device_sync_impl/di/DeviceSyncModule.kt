package io.paritytech.polkadotapp.feature_device_sync_impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.paritytech.polkadotapp.common.presentation.AppInitializer
import io.paritytech.polkadotapp.feature_device_sync_api.domain.DeviceSyncCoordinator
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallationManager
import io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation.ActiveInstallationRecordRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation.RealActiveInstallationRecordRepository
import io.paritytech.polkadotapp.feature_device_sync_impl.data.device.DeviceInfoProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.data.device.RealDeviceInfoProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.RealDeviceSyncCoordinator
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.activeInstallation.RealActiveInstallationManager
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.DeviceTransferKeyScheduleProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer.RealDeviceTransferKeyScheduleProvider
import io.paritytech.polkadotapp.feature_device_sync_impl.presentation.initialization.ActiveInstallationInitializer
import io.paritytech.polkadotapp.feature_device_sync_impl.presentation.initialization.DeviceSyncInitializer

@Module
@InstallIn(SingletonComponent::class)
internal interface DeviceSyncModule {
    @Binds
    fun bindDeviceSyncCoordinator(impl: RealDeviceSyncCoordinator): DeviceSyncCoordinator

    @Binds
    fun bindActiveInstallationManager(impl: RealActiveInstallationManager): ActiveInstallationManager

    @Binds
    fun bindActiveInstallationRecordRepository(
        impl: RealActiveInstallationRecordRepository
    ): ActiveInstallationRecordRepository

    @Binds
    fun bindDeviceTransferKeyScheduleProvider(
        impl: RealDeviceTransferKeyScheduleProvider
    ): DeviceTransferKeyScheduleProvider

    @Binds
    fun bindDeviceInfoProvider(impl: RealDeviceInfoProvider): DeviceInfoProvider

    @Binds
    @IntoSet
    fun bindInitializer(impl: DeviceSyncInitializer): AppInitializer

    @Binds
    @IntoSet
    fun bindActiveInstallationInitializer(impl: ActiveInstallationInitializer): AppInitializer
}
