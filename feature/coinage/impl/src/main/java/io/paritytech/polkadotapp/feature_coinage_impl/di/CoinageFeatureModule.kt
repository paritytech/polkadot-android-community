package io.paritytech.polkadotapp.feature_coinage_impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.CoinsInteractor
import io.paritytech.polkadotapp.feature_coinage_api.domain.RecyclerVouchersInteractor
import io.paritytech.polkadotapp.feature_coinage_api.domain.UnloadDelayStrategy
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.CoinAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.common.VoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentPlanner
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentService
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.ExternalPaymentWorkerStarter
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageBackupService
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageRecyclingSyncManager
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageServiceStarter
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinAmountBreakdownUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageRecyclingUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageTestHelperUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ForceReclaimCoinsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.OnboardingUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.PrepareCoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ShareCoinageLogsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ValidateTransferPlanUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.ConsumedTokenChecker
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.FreeUnloadTokenResolver
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.RealConsumedTokenChecker
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.RealFreeUnloadTokenResolver
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.RealUnloadTokenPeriodCalculator
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.UnloadTokenPeriodCalculator
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinageTransferWalRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.ExponentBoundsRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.RealCoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.RealCoinageTransferWalRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.RealExponentBoundsRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.RealRecyclerProofDataProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.RealVoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.RecyclerProofDataProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.context.CoinageSigningContextProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.context.RealCoinageSigningContextProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.CoinsBackupLastIndexStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.CoinsDeepBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.CoinsInitialBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.RealCoinsBackupLastIndexStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.RealCoinsDeepBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.RealCoinsInitialBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.RealVouchersBackupLastIndexStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.RealVouchersDeepBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.RealVouchersInitialBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.VouchersBackupLastIndexStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.VouchersDeepBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.VouchersInitialBackupCompletedStorage
import io.paritytech.polkadotapp.feature_coinage_impl.domain.RandomUnloadDelayStrategy
import io.paritytech.polkadotapp.feature_coinage_impl.domain.RealCoinsInteractor
import io.paritytech.polkadotapp.feature_coinage_impl.domain.RealVouchersInteractor
import io.paritytech.polkadotapp.feature_coinage_impl.domain.common.RealCoinAllocator
import io.paritytech.polkadotapp.feature_coinage_impl.domain.common.RealVoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.RealExternalPaymentPlanner
import io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.RealExternalPaymentService
import io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.RealExternalPaymentWorkerStarter
import io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.repository.ExternalPaymentRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.repository.RealExternalPaymentRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.usecase.RealUnloadRecyclerIntoExternalAssetUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.usecase.UnloadRecyclerIntoExternalAssetUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransactionFactory
import io.paritytech.polkadotapp.feature_coinage_impl.domain.service.RealCoinageBackupService
import io.paritytech.polkadotapp.feature_coinage_impl.domain.service.RealCoinageServiceStarter
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.CoinageTransferSubmissionUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealCoinAmountBreakdownUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealCoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealCoinageRecyclingUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealCoinageTestHelperUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealCoinageTransferSubmissionUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealCoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealForceReclaimCoinsUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealOnboardingUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealPrepareCoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealShareCoinageLogsUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealTotalBalanceUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase.RealValidateTransferPlanUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.worker.RealCoinageRecyclingSyncManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface CoinageFeatureModule {
    @Binds
    fun bindCoinsInitialBackupCompletedStorage(impl: RealCoinsInitialBackupCompletedStorage): CoinsInitialBackupCompletedStorage

    @Binds
    fun bindVouchersInitialBackupCompletedStorage(impl: RealVouchersInitialBackupCompletedStorage): VouchersInitialBackupCompletedStorage

    @Binds
    fun bindCoinsDeepBackupCompletedStorage(impl: RealCoinsDeepBackupCompletedStorage): CoinsDeepBackupCompletedStorage

    @Binds
    fun bindVouchersDeepBackupCompletedStorage(impl: RealVouchersDeepBackupCompletedStorage): VouchersDeepBackupCompletedStorage

    @Binds
    fun bindCoinsBackupLastIndexStorage(impl: RealCoinsBackupLastIndexStorage): CoinsBackupLastIndexStorage

    @Binds
    fun bindVouchersBackupLastIndexStorage(impl: RealVouchersBackupLastIndexStorage): VouchersBackupLastIndexStorage

    @Binds
    fun bindCoinAllocator(impl: RealCoinAllocator): CoinAllocator

    @Binds
    fun bindCoinRepository(impl: RealCoinRepository): CoinRepository

    @Binds
    fun bindVoucherAllocator(impl: RealVoucherAllocator): VoucherAllocator

    @Binds
    fun bindVoucherRepository(impl: RealVoucherRepository): VoucherRepository

    @Binds
    fun bindUnloadDelayStrategy(impl: RandomUnloadDelayStrategy): UnloadDelayStrategy

    @Binds
    fun bindOnboardingUseCase(impl: RealOnboardingUseCase): OnboardingUseCase

    @Binds
    fun bindVoucherInteractor(impl: RealVouchersInteractor): RecyclerVouchersInteractor

    @Binds
    fun bindCoinsInteractor(impl: RealCoinsInteractor): CoinsInteractor

    @Binds
    fun bindTotalBalanceUseCase(impl: RealTotalBalanceUseCase): TotalBalanceUseCase

    @Binds
    fun bindExponentBoundsRepository(impl: RealExponentBoundsRepository): ExponentBoundsRepository

    @Binds
    fun bindCoinageServiceStarter(impl: RealCoinageServiceStarter): CoinageServiceStarter

    @Binds
    fun bindTransferWalStore(impl: RealCoinageTransferWalRepository): CoinageTransferWalRepository

    @Binds
    fun bindCoinageBalanceConverterUseCase(impl: RealCoinageBalanceConverterUseCase): CoinageBalanceConverterUseCase

    @Binds
    fun bindCoinageSigningContextProvider(impl: RealCoinageSigningContextProvider): CoinageSigningContextProvider

    @Binds
    fun bindUnloadTokenResolver(impl: RealFreeUnloadTokenResolver): FreeUnloadTokenResolver

    @Binds
    fun bindConsumedTokenChecker(impl: RealConsumedTokenChecker): ConsumedTokenChecker

    @Binds
    fun bindCoinAmountBreakdownUseCase(impl: RealCoinAmountBreakdownUseCase): CoinAmountBreakdownUseCase

    @Binds
    fun bindPrepareCoinageTransferUseCase(impl: RealPrepareCoinageTransferUseCase): PrepareCoinageTransferUseCase

    @Binds
    fun bindUnloadTokenPeriodCalculator(impl: RealUnloadTokenPeriodCalculator): UnloadTokenPeriodCalculator

    @Binds
    fun bindCoinageTestHelperUseCase(impl: RealCoinageTestHelperUseCase): CoinageTestHelperUseCase

    @Binds
    fun bindRecyclerRevisionProvider(impl: RealRecyclerProofDataProvider): RecyclerProofDataProvider

    @Binds
    fun bindCoinageTransferSubmissionUseCase(impl: RealCoinageTransferSubmissionUseCase): CoinageTransferSubmissionUseCase

    @Binds
    fun bindCoinageReceiveCoinsUseCase(impl: RealCoinageTransferUseCase): CoinageTransferUseCase

    @Binds
    fun bindForceReclaimCoinsUseCase(impl: RealForceReclaimCoinsUseCase): ForceReclaimCoinsUseCase

    @Binds
    fun bindValidateTransferPlanUseCase(impl: RealValidateTransferPlanUseCase): ValidateTransferPlanUseCase

    @Binds
    fun bindShareCoinageLogsUseCase(impl: RealShareCoinageLogsUseCase): ShareCoinageLogsUseCase

    @Binds
    fun bindCoinageRecyclingUseCase(impl: RealCoinageRecyclingUseCase): CoinageRecyclingUseCase

    @Binds
    fun bindCoinageRecyclingSyncManager(impl: RealCoinageRecyclingSyncManager): CoinageRecyclingSyncManager

    @Binds
    @Singleton
    fun bindCoinageBackupService(impl: RealCoinageBackupService): CoinageBackupService

    // --- External payments (RFC-0006 host_payment_request) ---

    @Binds
    @Singleton
    fun bindExternalPaymentRepository(impl: RealExternalPaymentRepository): ExternalPaymentRepository

    @Binds
    @Singleton
    fun bindExternalPaymentService(impl: RealExternalPaymentService): ExternalPaymentService

    @Binds
    @Singleton
    fun bindExternalPaymentWorkerStarter(impl: RealExternalPaymentWorkerStarter): ExternalPaymentWorkerStarter

    @Binds
    fun bindExternalPaymentPlanner(impl: RealExternalPaymentPlanner): ExternalPaymentPlanner

    @Binds
    fun bindUnloadRecyclerIntoExternalAssetUseCase(
        impl: RealUnloadRecyclerIntoExternalAssetUseCase,
    ): UnloadRecyclerIntoExternalAssetUseCase

    @Binds
    fun bindCoinageTransactionFactory(impl: CoinageTransactionFactory): CoinageTransaction.Factory
}
