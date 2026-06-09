package io.paritytech.polkadotapp.feature_wallet_impl.domain.interactor

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.util.planksFromAmount
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.data.network.TestnetEnvironment
import io.paritytech.polkadotapp.common.utils.filterResultSuccess
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.getDepositAccount
import io.paritytech.polkadotapp.feature_coinage_api.domain.CoinsInteractor
import io.paritytech.polkadotapp.feature_coinage_api.domain.RecyclerVouchersInteractor
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.BackupProgress
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageBackupService
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageRecyclingUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageTestHelperUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ShareCoinageLogsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_fund_api.domain.AutoConvertDepositService
import io.paritytech.polkadotapp.feature_fund_api.domain.model.AutoConvertDeposit
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transfers_api.domain.usecase.TestnetFundUseCase
import io.paritytech.polkadotapp.feature_wallet_impl.domain.model.AssetInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DigitalDollarCardDetailsInteractor @Inject constructor(
    private val accountRepository: AccountRepository,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val totalBalanceUseCase: TotalBalanceUseCase,
    private val testnetFundUseCase: TestnetFundUseCase,
    private val environment: TestnetEnvironment,
    private val coinsInteractor: CoinsInteractor,
    private val recyclerVouchersInteractor: RecyclerVouchersInteractor,
    private val coinageTestHelperUseCase: CoinageTestHelperUseCase,
    private val autoConvertDepositService: AutoConvertDepositService,
    private val shareCoinageLogsUseCase: ShareCoinageLogsUseCase,
    private val coinageRecyclingUseCase: CoinageRecyclingUseCase,
    private val coinageBackupService: CoinageBackupService
) {
    companion object {
        private val TOP_UP_AMOUNT = 150.toBigDecimal()
        private val NIGHTLY_TOP_UP_AMOUNT = 10.toBigDecimal()
    }

    fun observeAssetInfo(): Flow<AssetInfo> = flow {
        val asset = chainAssetProvider.asset()
        emitAll(createAssetInfoFlow(asset))
    }

    fun observeCoins(): Flow<List<Coin>> = coinsInteractor.subscribeCoins()

    fun observeVouchers(): Flow<List<RecyclerVoucher>> = recyclerVouchersInteractor.subscribeVouchers()

    fun observeActionsEnabled(): Flow<Boolean> = coinageBackupService.subscribeProgress()
        .map { it.actionsEnabled() }

    fun observeBackupProgress(): Flow<BackupProgress> = coinageBackupService.subscribeProgress()

    context(ComputationalScope)
    fun startDeepSearch() = coinageBackupService.deepSearch()

    context(ComputationalScope)
    fun markBackupCompleted() = coinageBackupService.markAsCompleted()

    suspend fun autoFundAvailable() = environment != TestnetEnvironment.PRODUCTION

    suspend fun testnetFund(): Result<Unit> {
        val chainAsset = chainAssetProvider()
        val amount = when (environment) {
            TestnetEnvironment.TESTNET -> TOP_UP_AMOUNT
            TestnetEnvironment.NIGHTLY, TestnetEnvironment.PRODUCTION -> NIGHTLY_TOP_UP_AMOUNT
        }
        val topUpAmount = amount.planksFromAmount(chainAsset.asset.precision)
        val recipientAccountId = accountRepository.getDepositAccount().accountIdIn(chainAsset.chain)

        return testnetFundUseCase(chainAsset, topUpAmount, recipientAccountId)
            .map { autoConvertDepositService.currentDeposit.filter { it?.status is AutoConvertDeposit.Status.Done }.first() }
    }

    suspend fun makeAllVouchersReady() = coinageTestHelperUseCase.makeAllVouchersReady()

    suspend fun shareCoinageLogs(): Result<Unit> = shareCoinageLogsUseCase().map { }

    suspend fun forceRecycle(coin: Coin): Result<Unit> = coinageRecyclingUseCase.recycle(listOf(coin)).map { }

    private fun createAssetInfoFlow(asset: Chain.Asset) = totalBalanceUseCase.subscribeTotalBalance()
        .logFailure("DigitalDollarCardDetailsInteractor: Failed to get coinage balance")
        .filterResultSuccess()
        .filterNotNull()
        .map { balance ->
            AssetInfo(
                asset = asset,
                totalBalance = balance.totalBalance,
                spendableSecuredBalance = balance.spendableBalance.secured,
                spendableDegradedBalance = balance.spendableBalance.degraded,
                pendingBalance = balance.pendingBalance,
            )
        }

    private fun BackupProgress.actionsEnabled() = this !is BackupProgress.Initial && this !is BackupProgress.Deep
}
