package io.paritytech.polkadotapp.feature_connection_status_impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.paritytech.polkadotapp.feature_connection_status_api.domain.ConnectionStatusMonitor
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ConnectionStatusMixin
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.RealConnectionStatusMonitor
import io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.RealConnectionStatusMixinFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ConnectionStatusFeatureModule {
    @Binds
    @Singleton
    fun bindConnectionStatusMonitor(impl: RealConnectionStatusMonitor): ConnectionStatusMonitor

    @Binds
    fun bindConnectionStatusMixinFactory(impl: RealConnectionStatusMixinFactory): ConnectionStatusMixin.Factory
}
