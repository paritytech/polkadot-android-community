package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.domain.BalanceDetailsInteractor
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.domain.RealBalanceDetailsInteractor

@Module
@InstallIn(ViewModelComponent::class)
interface BalanceDetailsModule {
    @Binds
    @ViewModelScoped
    fun bindBalanceDetailsInteractor(impl: RealBalanceDetailsInteractor): BalanceDetailsInteractor
}
