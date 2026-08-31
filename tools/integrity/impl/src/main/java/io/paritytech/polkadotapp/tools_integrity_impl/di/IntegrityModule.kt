package io.paritytech.polkadotapp.tools_integrity_impl.di

import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.tools_integrity_api.interceptors.BackendIntegrityInterceptor
import io.paritytech.polkadotapp.tools_integrity_impl.data.integrity.PlayIntegrityManager
import io.paritytech.polkadotapp.tools_integrity_impl.data.integrity.RealPlayIntegrityManager
import io.paritytech.polkadotapp.tools_integrity_impl.data.interceptors.RealBackendIntegrityInterceptor

@Module
@InstallIn(SingletonComponent::class)
interface IntegrityModule {
    companion object {
        @Provides
        fun provideIntegrityManager(
            contextManager: ContextManager,
        ): IntegrityManager {
            return IntegrityManagerFactory.create(contextManager.applicationContext)
        }
    }

    @Binds
    fun bindBackendIntegrityInterceptor(impl: RealBackendIntegrityInterceptor): BackendIntegrityInterceptor

    @Binds
    fun bindPlayIntegrityManager(impl: RealPlayIntegrityManager): PlayIntegrityManager
}
