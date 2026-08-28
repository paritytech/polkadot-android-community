package io.paritytech.polkadotapp.feature_products_impl.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.paritytech.polkadotapp.common.BuildConfig
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.ExternalExtensionProvider
import io.paritytech.polkadotapp.feature_chats_api.domain.search.ChatSearchResultProvider
import io.paritytech.polkadotapp.feature_dotns_api.presentation.DotNsServingHostResolver
import io.paritytech.polkadotapp.feature_products_api.domain.ProductAccountIdProvider
import io.paritytech.polkadotapp.feature_products_api.domain.ProductRequestAccountResolver
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.AccountsProtocol
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.MembersRingLocator
import io.paritytech.polkadotapp.feature_products_api.domain.browser.ProductSessionController
import io.paritytech.polkadotapp.feature_products_api.domain.deriveEntropy.DeriveEntropyUseCase
import io.paritytech.polkadotapp.feature_products_api.domain.runtime.ProductRuntimeSettings
import io.paritytech.polkadotapp.feature_products_api.domain.sponsoring.PreimageSubmitSponsoring
import io.paritytech.polkadotapp.feature_products_api.domain.sponsoring.StatementStoreSubmissionSponsoring
import io.paritytech.polkadotapp.feature_products_api.domain.sponsoring.TransactionSponsoring
import io.paritytech.polkadotapp.feature_products_api.presentation.spaHost.SpaHost
import io.paritytech.polkadotapp.feature_products_impl.data.repository.BrowserTabRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductIntegrationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.RealBrowserTabRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.RealProductIntegrationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.RealProductRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.RealRingVrfKeyRegistrationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.RingVrfKeyRegistrationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.scheduledNotification.RealScheduledProductNotificationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.scheduledNotification.ScheduledProductNotificationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.storage.AssetContainerScriptProvider
import io.paritytech.polkadotapp.feature_products_impl.data.storage.ContainerScriptProvider
import io.paritytech.polkadotapp.feature_products_impl.data.storage.ProductLocalStorage
import io.paritytech.polkadotapp.feature_products_impl.data.storage.RealProductLocalStorage
import io.paritytech.polkadotapp.feature_products_impl.domain.ProductAccountDerivationUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.RealProductRequestAccountResolver
import io.paritytech.polkadotapp.feature_products_impl.domain.accountsProtocol.RealAccountsProtocol
import io.paritytech.polkadotapp.feature_products_impl.domain.accountsProtocol.RealRingVrfKeySource
import io.paritytech.polkadotapp.feature_products_impl.domain.accountsProtocol.RingVrfKeySource
import io.paritytech.polkadotapp.feature_products_impl.domain.accountsProtocol.locator.RealMembersRingLocator
import io.paritytech.polkadotapp.feature_products_impl.domain.accountsProtocol.registry.RealRingVrfKeyRegistry
import io.paritytech.polkadotapp.feature_products_impl.domain.accountsProtocol.registry.RingVrfKeyRegistry
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.external.ProductExternalExtensionProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.menu.ProductChatMenuInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.menu.RealProductChatMenuInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.browser.RealProductSessionController
import io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof.CrossProductProofRequester
import io.paritytech.polkadotapp.feature_products_impl.domain.crossProductProof.RealCrossProductProofRequester
import io.paritytech.polkadotapp.feature_products_impl.domain.deriveEntropy.RealDeriveEntropyUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreProductsService
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.RealExploreProductsService
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.allowance.AllowanceKeyStorage
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.allowance.RealAllowanceKeyStorage
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.sponsoring.RealStatementStoreSubmissionSponsoring
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.sponsoring.SponsorPreimageWithBulletin
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.sponsoring.SponsorReviveCallsWithPgas
import io.paritytech.polkadotapp.feature_products_impl.domain.notifications.ProductNotificationScheduler
import io.paritytech.polkadotapp.feature_products_impl.domain.notifications.RealProductNotificationScheduler
import io.paritytech.polkadotapp.feature_products_impl.domain.origin.ProductAccountOrigins
import io.paritytech.polkadotapp.feature_products_impl.domain.origin.RealProductAccountOrigins
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.ProductPermissionGuard
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.ProductPermissionRepository
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.ProductPermissionRequester
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.RealProductPermissionGuard
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.RealProductPermissionRepository
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.RealProductPermissionRequester
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.handlers.AccountAccessPermissionHandler
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.handlers.BalanceAccessPermissionHandler
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.handlers.DeviceCapabilityPermissionHandler
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.handlers.ProductPermissionHandler
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.handlers.RemotePermissionHandler
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.handlers.UserIdentityAccessPermissionHandler
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductRegistrar
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductScriptResolver
import io.paritytech.polkadotapp.feature_products_impl.domain.product.RealProductRegistrar
import io.paritytech.polkadotapp.feature_products_impl.domain.product.RealProductScriptResolver
import io.paritytech.polkadotapp.feature_products_impl.domain.productBotManagement.ProductBotManagementInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.productBotManagement.RealProductBotManagementInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.runtime.PrefsProductRuntimeSettings
import io.paritytech.polkadotapp.feature_products_impl.domain.search.ProductChatSearchResultProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.serialization.JsWidgetSerializer
import io.paritytech.polkadotapp.feature_products_impl.domain.serialization.ScaleWidgetSerializer
import io.paritytech.polkadotapp.feature_products_impl.domain.spaBrowser.RealSpaBrowserInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.spaBrowser.SpaBrowserInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.ExecuteTopUpUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest.RealExecuteTopUpUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.usecase.RealResolveProductUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.usecase.ResolveProductUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.webView.ProductServingHostResolver
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaHost.RuntimeSelectingSpaHost
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ProductsModule {
    companion object {
        @Provides
        @Singleton
        @TrUAPIChainHttpClient
        fun provideTrUAPIChainHttpClient(shared: OkHttpClient): OkHttpClient =
            shared.newBuilder()
                // Chain sockets are long-lived subscriptions: the shared client's
                // read timeout would kill them when idle, so detect dead peers
                // with pings instead. newBuilder keeps the shared pool/dispatcher.
                .readTimeout(0, TimeUnit.SECONDS)
                .pingInterval(CHAIN_SOCKET_PING_SECONDS, TimeUnit.SECONDS)
                .build()

        private const val CHAIN_SOCKET_PING_SECONDS = 30L

        @Provides
        @Singleton
        fun provideProductRuntimeSettings(@ApplicationContext context: Context): ProductRuntimeSettings =
            PrefsProductRuntimeSettings(
                prefs = context.getSharedPreferences("product_runtime_settings", Context.MODE_PRIVATE),
                isDebugBuild = BuildConfig.DEBUG,
            )
    }

    @Binds
    @Singleton
    fun bindWidgetSerializer(impl: ScaleWidgetSerializer): JsWidgetSerializer

    @Binds
    @Singleton
    fun bindProductSessionController(impl: RealProductSessionController): ProductSessionController

    @Binds
    @Singleton
    fun bindBrowserTabRepository(impl: RealBrowserTabRepository): BrowserTabRepository

    @Binds
    @Singleton
    fun bindSpaHost(impl: RuntimeSelectingSpaHost): SpaHost

    @Binds
    @Singleton
    fun bindProductAccountIdProvider(impl: ProductAccountDerivationUseCase): ProductAccountIdProvider

    @Binds
    @Singleton
    fun bindProductRepository(impl: RealProductRepository): ProductRepository

    @Binds
    @Singleton
    fun bindResolveProductUseCase(impl: RealResolveProductUseCase): ResolveProductUseCase

    @Binds
    @Singleton
    fun bindServingHostResolver(impl: ProductServingHostResolver): DotNsServingHostResolver

    @Binds
    @Singleton
    fun bindContainerScriptProvider(impl: AssetContainerScriptProvider): ContainerScriptProvider

    @Binds
    @Singleton
    fun bindProductBotManagementInteractor(impl: RealProductBotManagementInteractor): ProductBotManagementInteractor

    @Binds
    @IntoSet
    fun bindProductExternalExtensionProvider(impl: ProductExternalExtensionProvider): ExternalExtensionProvider

    @Binds
    @IntoSet
    fun bindProductChatSearchResultProvider(impl: ProductChatSearchResultProvider): ChatSearchResultProvider

    @Binds
    fun bindProductLocalStorage(impl: RealProductLocalStorage): ProductLocalStorage

    @Binds
    fun bindProductAccountOrigins(impl: RealProductAccountOrigins): ProductAccountOrigins

    @Binds
    fun bindMembersRingLocator(impl: RealMembersRingLocator): MembersRingLocator

    @Binds
    fun bindRingVrfKeyRegistrationRepository(
        impl: RealRingVrfKeyRegistrationRepository
    ): RingVrfKeyRegistrationRepository

    @Binds
    fun bindRingVrfKeyRegistry(impl: RealRingVrfKeyRegistry): RingVrfKeyRegistry

    @Binds
    fun bindRingVrfKeySource(impl: RealRingVrfKeySource): RingVrfKeySource

    @Binds
    @Singleton
    fun bindProductPermissionRepository(impl: RealProductPermissionRepository): ProductPermissionRepository

    @Binds
    fun bindProductPermissionGuard(impl: RealProductPermissionGuard): ProductPermissionGuard

    @Binds
    fun bindCrossProductProofRequester(impl: RealCrossProductProofRequester): CrossProductProofRequester

    @Binds
    fun bindRemotePermissionHandler(
        impl: RemotePermissionHandler,
    ): ProductPermissionHandler<ProductPermission.RemotePermission>

    @Binds
    fun bindAccountAccessPermissionHandler(
        impl: AccountAccessPermissionHandler,
    ): ProductPermissionHandler<ProductPermission.AccountAccess>

    @Binds
    fun bindBalanceAccessPermissionHandler(
        impl: BalanceAccessPermissionHandler,
    ): ProductPermissionHandler<ProductPermission.BalanceAccess>

    @Binds
    fun bindDeviceCapabilityPermissionHandler(
        impl: DeviceCapabilityPermissionHandler,
    ): ProductPermissionHandler<ProductPermission.DeviceCapability>

    @Binds
    fun bindUserIdentityAccessPermissionHandler(
        impl: UserIdentityAccessPermissionHandler,
    ): ProductPermissionHandler<ProductPermission.UserIdentityAccess>

    @Binds
    @Singleton
    fun bindPermissionRequester(impl: RealProductPermissionRequester): ProductPermissionRequester

    @Binds
    @Singleton
    fun bindProductScriptResolver(impl: RealProductScriptResolver): ProductScriptResolver

    @Binds
    @Singleton
    fun bindProductRegistrar(impl: RealProductRegistrar): ProductRegistrar

    @Binds
    @Singleton
    fun bindProductIntegrationRepository(impl: RealProductIntegrationRepository): ProductIntegrationRepository

    @Binds
    fun bindSpaBrowserInteractor(impl: RealSpaBrowserInteractor): SpaBrowserInteractor

    @Binds
    fun bindProductChatMenuInteractor(impl: RealProductChatMenuInteractor): ProductChatMenuInteractor

    @Binds
    @Singleton
    fun bindAllowanceKeyStorage(impl: RealAllowanceKeyStorage): AllowanceKeyStorage

    @Binds
    @Singleton
    fun bindAccountsProtocol(impl: RealAccountsProtocol): AccountsProtocol

    @Binds
    fun bindTransactionSponsoring(impl: SponsorReviveCallsWithPgas): TransactionSponsoring

    @Binds
    fun bindPreimageSubmitSponsoring(impl: SponsorPreimageWithBulletin): PreimageSubmitSponsoring

    @Binds
    fun bindStatementStoreSubmissionSponsoring(impl: RealStatementStoreSubmissionSponsoring): StatementStoreSubmissionSponsoring

    @Binds
    fun bindExploreProductsService(impl: RealExploreProductsService): ExploreProductsService

    @Binds
    @Singleton
    fun bindScheduledProductNotificationRepository(
        impl: RealScheduledProductNotificationRepository,
    ): ScheduledProductNotificationRepository

    @Binds
    @Singleton
    fun bindProductNotificationScheduler(impl: RealProductNotificationScheduler): ProductNotificationScheduler

    @Binds
    fun bindProductRequestAccountResolver(impl: RealProductRequestAccountResolver): ProductRequestAccountResolver

    @Binds
    fun bindDeriveEntropyUseCase(impl: RealDeriveEntropyUseCase): DeriveEntropyUseCase

    @Binds
    fun bindExecuteTopUpUseCase(impl: RealExecuteTopUpUseCase): ExecuteTopUpUseCase
}
