package io.paritytech.polkadotapp.feature_products_impl.presentation.signTransaction.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContext
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningContextHolder
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody
import io.paritytech.polkadotapp.feature_products_impl.domain.signTransaction.CreateTransactionInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.signTransaction.SignPayloadJsonInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.signTransaction.SignRawInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.signTransaction.TransactionSignInteractor

@Module
@InstallIn(ViewModelComponent::class)
class TransactionSignModule {
    @Provides
    fun provideSigningContext(
        signingContextHolder: SigningContextHolder,
    ): SigningContext {
        return requireNotNull(signingContextHolder.get()) {
            "SigningContext is not set. The signing screen was likely restored after process death."
        }
    }

    @Provides
    fun provideTransactionSignInteractor(
        signingContext: SigningContext,
        signPayloadJsonInteractorFactory: SignPayloadJsonInteractor.Factory,
        signRawInteractorFactory: SignRawInteractor.Factory,
        createTransactionInteractorFactory: CreateTransactionInteractor.Factory,
    ): TransactionSignInteractor {
        return when (val body = signingContext.signingRequestBody) {
            is SigningRequestBody.Transaction -> signPayloadJsonInteractorFactory.create(body.payload)
            is SigningRequestBody.Raw -> signRawInteractorFactory.create(body.payload)
            is SigningRequestBody.CreateTransaction -> createTransactionInteractorFactory.create(body.payload)
        }
    }
}
