package io.paritytech.polkadotapp.feature_chats_impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoSet
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatFaqInteractor
import io.paritytech.polkadotapp.feature_chats_impl.domain.interactors.AddContactInteractor
import io.paritytech.polkadotapp.feature_chats_impl.domain.interactors.ChatListInteractor
import io.paritytech.polkadotapp.feature_chats_impl.domain.interactors.RealAddContactInteractor
import io.paritytech.polkadotapp.feature_chats_impl.domain.interactors.RealChatFaqInteractor
import io.paritytech.polkadotapp.feature_chats_impl.domain.interactors.RealChatListInteractor
import io.paritytech.polkadotapp.feature_chats_impl.domain.usecase.RealWaitForChatExistsUseCase
import io.paritytech.polkadotapp.feature_chats_impl.domain.usecase.WaitForChatExistsUseCase
import io.paritytech.polkadotapp.feature_chats_impl.presentation.search.scan.ContactAddressScanContentParser
import io.paritytech.polkadotapp.feature_scan_api.domain.ScanContentParser

@Module
@InstallIn(ViewModelComponent::class)
interface ChatsFeatureModule {
    @Binds
    fun bindChatListInteractor(impl: RealChatListInteractor): ChatListInteractor

    @Binds
    fun bindAddContactInteractor(impl: RealAddContactInteractor): AddContactInteractor

    @Binds
    fun bindWaitForChatExistsUseCase(impl: RealWaitForChatExistsUseCase): WaitForChatExistsUseCase

    @Binds
    fun bindChatFaqInteractor(impl: RealChatFaqInteractor): ChatFaqInteractor

    @Binds
    @IntoSet
    fun bindContactAddressScanContentParser(impl: ContactAddressScanContentParser): ScanContentParser
}
