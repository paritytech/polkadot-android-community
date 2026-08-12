package io.paritytech.polkadotapp.feature_products_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import io.paritytech.polkadotapp.feature_account_api.di.AccountPurposeKey
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.AccountDerivationProvider
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.RingVrfDerivationProvider
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_products_api.model.derivation.ReservedProductIds
import io.paritytech.polkadotapp.feature_products_impl.domain.derivation.ReservedProductAccountDerivationProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.derivation.ReservedRingVrfDerivationProvider

private val FULL_PERSONHOOD_INDEX = DerivationIndex32.fromUInt(0u)
private val LIGHT_PERSONHOOD_INDEX = DerivationIndex32.fromUInt(1u)

@Module
@InstallIn(SingletonComponent::class)
internal object ProductDerivationModule {
    @Provides
    @IntoMap
    @AccountPurposeKey(MetaAccount.Purpose.WALLET)
    fun provideWalletAccountDerivation(): AccountDerivationProvider {
        return ReservedProductAccountDerivationProvider(ReservedProductIds.LIGHT_PERSON_IDENTITY)
    }

    @Provides
    @IntoMap
    @AccountPurposeKey(MetaAccount.Purpose.DEPOSIT)
    fun provideDepositAccountDerivation(): AccountDerivationProvider {
        return ReservedProductAccountDerivationProvider(ReservedProductIds.FUNDING)
    }

    @Provides
    @IntoMap
    @AccountPurposeKey(MetaAccount.Purpose.CANDIDATE)
    fun provideCandidateAccountDerivation(): AccountDerivationProvider {
        return ReservedProductAccountDerivationProvider(ReservedProductIds.GAME)
    }

    @Provides
    @IntoMap
    @AccountPurposeKey(MetaAccount.Purpose.CANDIDATE)
    fun provideFullPersonhoodRingVrfDerivation(): RingVrfDerivationProvider {
        return ReservedRingVrfDerivationProvider(ReservedProductIds.PERSONHOOD, FULL_PERSONHOOD_INDEX)
    }

    @Provides
    @IntoMap
    @AccountPurposeKey(MetaAccount.Purpose.WALLET)
    fun provideLightPersonhoodRingVrfDerivation(): RingVrfDerivationProvider {
        return ReservedRingVrfDerivationProvider(ReservedProductIds.PERSONHOOD, LIGHT_PERSONHOOD_INDEX)
    }
}
