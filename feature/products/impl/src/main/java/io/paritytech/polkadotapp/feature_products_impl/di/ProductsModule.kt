package io.paritytech.polkadotapp.feature_products_impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.ExternalExtensionProvider
import io.paritytech.polkadotapp.feature_products_api.domain.GetContextualAliasUseCase
import io.paritytech.polkadotapp.feature_products_api.domain.ProductAccountIdProvider
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.AccountsProtocol
import io.paritytech.polkadotapp.feature_products_api.domain.deriveEntropy.DeriveEntropyUseCase
import io.paritytech.polkadotapp.feature_products_api.domain.sponsoring.PreimageSubmitSponsoring
import io.paritytech.polkadotapp.feature_products_api.domain.sponsoring.StatementStoreSubmissionSponsoring
import io.paritytech.polkadotapp.feature_products_api.domain.sponsoring.TransactionSponsoring
import io.paritytech.polkadotapp.feature_products_api.presentation.spaHost.SpaHost
import io.paritytech.polkadotapp.feature_products_impl.data.network.HttpProductScriptDownloader
import io.paritytech.polkadotapp.feature_products_impl.data.network.ProductScriptDownloader
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductIntegrationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.ProductRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.RealProductIntegrationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.repository.RealProductRepository
import io.paritytech.polkadotapp.feature_products_impl.data.scheduledNotification.RealScheduledProductNotificationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.scheduledNotification.ScheduledProductNotificationRepository
import io.paritytech.polkadotapp.feature_products_impl.data.storage.AssetContainerScriptProvider
import io.paritytech.polkadotapp.feature_products_impl.data.storage.ContainerScriptProvider
import io.paritytech.polkadotapp.feature_products_impl.data.storage.ProductLocalStorage
import io.paritytech.polkadotapp.feature_products_impl.data.storage.RealProductLocalStorage
import io.paritytech.polkadotapp.feature_products_impl.domain.ProductAccountDerivationUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.accountsProtocol.RealAccountsProtocol
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.external.ProductExternalExtensionProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.menu.ProductChatMenuInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.bot.menu.RealProductChatMenuInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.deriveEntropy.RealDeriveEntropyUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreProductsService
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.RealExploreProductsService
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.RealGetContextualAliasUseCase
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
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ArchiveScriptResolver
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductRegistrar
import io.paritytech.polkadotapp.feature_products_impl.domain.product.ProductScriptResolver
import io.paritytech.polkadotapp.feature_products_impl.domain.product.RealProductRegistrar
import io.paritytech.polkadotapp.feature_products_impl.domain.productBotManagement.ProductBotManagementInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.productBotManagement.RealProductBotManagementInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.serialization.JsWidgetSerializer
import io.paritytech.polkadotapp.feature_products_impl.domain.serialization.ScaleWidgetSerializer
import io.paritytech.polkadotapp.feature_products_impl.domain.spaBrowser.RealSpaBrowserInteractor
import io.paritytech.polkadotapp.feature_products_impl.domain.spaBrowser.SpaBrowserInteractor
import io.paritytech.polkadotapp.feature_products_impl.presentation.spaHost.RealSpaHost
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ProductsModule {
    @Binds
    @Singleton
    fun bindWidgetSerializer(impl: ScaleWidgetSerializer): JsWidgetSerializer

    @Binds
    @Singleton
    fun bindSpaHost(impl: RealSpaHost): SpaHost

    @Binds
    @Singleton
    fun bindProductAccountIdProvider(impl: ProductAccountDerivationUseCase): ProductAccountIdProvider

    @Binds
    @Singleton
    fun bindProductRepository(impl: RealProductRepository): ProductRepository

    @Binds
    @Singleton
    fun bindContainerScriptProvider(impl: AssetContainerScriptProvider): ContainerScriptProvider

    @Binds
    @Singleton
    fun bindProductScriptDownloader(impl: HttpProductScriptDownloader): ProductScriptDownloader

    @Binds
    @Singleton
    fun bindProductBotManagementInteractor(impl: RealProductBotManagementInteractor): ProductBotManagementInteractor

    @Binds
    @IntoSet
    fun bindProductExternalExtensionProvider(impl: ProductExternalExtensionProvider): ExternalExtensionProvider

    @Binds
    fun bindProductLocalStorage(impl: RealProductLocalStorage): ProductLocalStorage

    @Binds
    fun bindProductAccountOrigins(impl: RealProductAccountOrigins): ProductAccountOrigins

    @Binds
    fun bindGetContextualAliasUseCase(impl: RealGetContextualAliasUseCase): GetContextualAliasUseCase

    @Binds
    @Singleton
    fun bindProductPermissionRepository(impl: RealProductPermissionRepository): ProductPermissionRepository

    @Binds
    fun bindProductPermissionGuard(impl: RealProductPermissionGuard): ProductPermissionGuard

    @Binds
    @Singleton
    fun bindPermissionRequester(impl: RealProductPermissionRequester): ProductPermissionRequester

    @Binds
    @Singleton
    fun bindProductScriptResolver(impl: ArchiveScriptResolver): ProductScriptResolver

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
    fun bindDeriveEntropyUseCase(impl: RealDeriveEntropyUseCase): DeriveEntropyUseCase
}
