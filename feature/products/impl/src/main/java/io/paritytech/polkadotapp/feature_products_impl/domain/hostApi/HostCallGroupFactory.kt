package io.paritytech.polkadotapp.feature_products_impl.domain.hostApi

import io.paritytech.polkadotapp.feature_products_impl.data.storage.ProductLocalStorage
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.ProductsBotApi
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.AccountHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.AllowanceHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.ChainHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.ChatHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.EntropyHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.HostCallHandlerGroup
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.NavigationHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.NotificationHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.PaymentHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.PermissionHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.PreimageHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.SigningHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.StatementHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.StorageHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.ThemeHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.handlerGroups.UserIdHostCalls
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.navigation.NavigationPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates host call handler groups from shared Dagger-managed dependencies.
 *
 * Callers provide per-environment params ([CallingProductIdProvider], [NavigationPolicy], [ProductsBotApi]).
 * The factory fills in singleton deps (interactor, local storage, signing context).
 */
@Singleton
class HostCallGroupFactory @Inject constructor(
    private val productLocalStorage: ProductLocalStorage,
) {
    /**
     * Shared handler groups used by ALL environments.
     */
    fun createShared(
        botApi: ProductsBotApi,
        productIdProvider: CallingProductIdProvider,
        navigationPolicy: NavigationPolicy,
    ): List<HostCallHandlerGroup> = listOf(
        AccountHostCalls(botApi, productIdProvider),
        ChainHostCalls(botApi),
        SigningHostCalls(botApi),
        StorageHostCalls(productLocalStorage, productIdProvider),
        NavigationHostCalls(navigationPolicy, productIdProvider),
        StatementHostCalls(botApi, productIdProvider),
        PreimageHostCalls(botApi, productIdProvider),
        PermissionHostCalls(botApi, productIdProvider),
        NotificationHostCalls(botApi, productIdProvider),
        EntropyHostCalls(botApi, productIdProvider),
        PaymentHostCalls(botApi, productIdProvider),
        UserIdHostCalls(botApi, productIdProvider),
        AllowanceHostCalls(botApi, productIdProvider),
        ThemeHostCalls(botApi),
    )

    /**
     * Chat-only handler group. Add to the shared list for chat environments.
     */
    fun createChatGroup(botApi: ProductsBotApi): HostCallHandlerGroup =
        ChatHostCalls(botApi)
}
