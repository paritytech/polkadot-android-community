package io.paritytech.polkadotapp.feature_videogame_impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.paritytech.polkadotapp.feature_videogame_impl.VideoGameNotificationPublisher
import io.paritytech.polkadotapp.feature_videogame_impl.data.notifications.RealVideoGameNotificationPublisher
import io.paritytech.polkadotapp.feature_videogame_impl.domain.notifications.RealVideoGameReminderScheduler
import io.paritytech.polkadotapp.feature_videogame_impl.domain.notifications.VideoGameReminderScheduler

@InstallIn(SingletonComponent::class)
@Module
internal interface VideoGameFeatureModule {
    @Binds
    fun bindVideoGameReminderScheduler(impl: RealVideoGameReminderScheduler): VideoGameReminderScheduler

    @Binds
    fun bindVideoGameNotificationPublisher(impl: RealVideoGameNotificationPublisher): VideoGameNotificationPublisher
}
