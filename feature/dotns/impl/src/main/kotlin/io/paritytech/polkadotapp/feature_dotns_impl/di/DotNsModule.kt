package io.paritytech.polkadotapp.feature_dotns_impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsContentSeeder
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_dotns_impl.data.config.DotNsConfigProvider
import io.paritytech.polkadotapp.feature_dotns_impl.data.config.RemoteConfigDotNsConfigProvider
import io.paritytech.polkadotapp.feature_dotns_impl.data.contract.DotNsContractApi
import io.paritytech.polkadotapp.feature_dotns_impl.data.contract.RealDotNsContractApi
import io.paritytech.polkadotapp.feature_dotns_impl.data.ipfs.CarFetcher
import io.paritytech.polkadotapp.feature_dotns_impl.data.ipfs.RealCarFetcher
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.ContentHashOverrides
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.DotNsContentStorage
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.RealDotNsContentStorage
import io.paritytech.polkadotapp.feature_dotns_impl.data.storage.SharedPrefsContentHashOverrides
import io.paritytech.polkadotapp.feature_dotns_impl.domain.dotNs.RealDotNsContentSeeder
import io.paritytech.polkadotapp.feature_dotns_impl.domain.dotNs.RealDotNsResolver
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DotNsModule {
    @Binds
    @Singleton
    fun bindDotNsResolver(impl: RealDotNsResolver): DotNsResolver

    @Binds
    @Singleton
    fun bindDotNsContractApi(impl: RealDotNsContractApi): DotNsContractApi

    @Binds
    @Singleton
    fun bindCarFetcher(impl: RealCarFetcher): CarFetcher

    @Binds
    @Singleton
    fun bindDotNsContentStorage(impl: RealDotNsContentStorage): DotNsContentStorage

    @Binds
    @Singleton
    fun bindContentHashCache(impl: SharedPrefsContentHashOverrides): ContentHashOverrides

    @Binds
    @Singleton
    fun bindDotNsContentSeeder(impl: RealDotNsContentSeeder): DotNsContentSeeder

    @Binds
    @Singleton
    fun bindDotNsConfigProvider(impl: RemoteConfigDotNsConfigProvider): DotNsConfigProvider
}
