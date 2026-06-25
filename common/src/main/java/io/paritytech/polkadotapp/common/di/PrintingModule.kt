package io.paritytech.polkadotapp.common.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.paritytech.polkadotapp.common.domain.printing.ReceiptPrinter
import io.paritytech.polkadotapp.common.domain.printing.SunmiReceiptPrinter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface PrintingModule {
    @Binds
    @Singleton
    fun bindReceiptPrinter(impl: SunmiReceiptPrinter): ReceiptPrinter
}
