package io.paritytech.polkadotapp.feature_products_impl.presentation.topUpRequest.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.RealTopUpRequestInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.TopUpRequestContext
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.TopUpRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.TopUpRequestInteractor

@Module
@InstallIn(ViewModelComponent::class)
interface TopUpRequestModule {
    @Binds
    @ViewModelScoped
    fun bindTopUpRequestInteractor(impl: RealTopUpRequestInteractor): TopUpRequestInteractor

    companion object {
        @Provides
        fun provideTopUpRequestContext(
            holder: TopUpRequestContextHolder,
        ): TopUpRequestContext {
            return requireNotNull(holder.get()) {
                "TopUpRequestContext is not set. The top-up prompt was likely restored after process death."
            }
        }
    }
}
