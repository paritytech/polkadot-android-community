package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.withAmount
import io.paritytech.polkadotapp.common.BuildConfig
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.disable
import io.paritytech.polkadotapp.common.utils.enable
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.BackupProgress
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_wallet_impl.PocketRouter
import io.paritytech.polkadotapp.feature_wallet_impl.domain.interactor.DigitalDollarCardDetailsInteractor
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.BalanceRestoreUiState
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.CoinageUiState
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.DigitalDollarCardDetailsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DigitalDollarCardDetailsViewModel @Inject constructor(
    private val interactor: DigitalDollarCardDetailsInteractor,
    private val router: PocketRouter,
    private val tokenAmountMapper: TokenAmountMapper
) : BaseViewModel() {
    private val fundInProgress = MutableStateFlow(false)

    val coinageState: StateFlow<LoadingState<CoinageUiState>> = combine(
        interactor.observeAssetInfo(),
        fundInProgress,
        interactor.observeCoins(),
        interactor.observeVouchers(),
        interactor.observeActionsEnabled()
    ) { assetInfo, inProgress, coins, vouchers, actionsEnabled ->
        val asset = assetInfo.asset

        LoadingState.Loaded(
            CoinageUiState(
                tokensState = CoinageUiState.TokensState(
                    totalBalance = tokenAmountMapper.mapFrom(asset.withAmount(assetInfo.totalBalance)),
                    spendableSecuredBalance = tokenAmountMapper.mapFrom(asset.withAmount(assetInfo.spendableSecuredBalance)),
                    spendableDegradedBalance = tokenAmountMapper.mapFrom(asset.withAmount(assetInfo.spendableDegradedBalance)),
                    pendingBalance = tokenAmountMapper.mapFrom(asset.withAmount(assetInfo.pendingBalance)),
                    coinList = coins,
                    voucherList = vouchers
                ),
                autoFundAvailable = interactor.autoFundAvailable(),
                fundInProgress = inProgress,
                actionsEnabled = actionsEnabled,
                coinageWidgetsEnabled = BuildConfig.COINAGE_WIDGETS_ENABLED,
                testnetFundEnabled = BuildConfig.TESTNET_FUND_ENABLED,
            )
        )
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = LoadingState.Loading
    )

    val state: StateFlow<DigitalDollarCardDetailsUiState> = interactor.observeBackupProgress()
        .map {
            DigitalDollarCardDetailsUiState(
                it.toBalanceRestoreUiState()
            )
        }
        .stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = DigitalDollarCardDetailsUiState(BalanceRestoreUiState.NotDetermined)
        )

    fun onFundClick() {
        router.openSelectFundAsset()
    }

    fun onSendClick() {
        router.openSendPayment()
    }

    fun onAutoFundClick() = launchUnit {
        if (fundInProgress.value) return@launchUnit
        fundInProgress.enable()
        interactor.testnetFund()
            .logFailure("Failed to perform testnet fund")
            .onFailure { showMessage("Failed to fund account") }
        fundInProgress.disable()
    }

    fun makeAllVouchersReady() = launchUnit {
        interactor.makeAllVouchersReady()
    }

    fun onShareLogsClick() = launchUnit {
        interactor.shareCoinageLogs()
            .onFailure { showMessage("Failed to share coinage logs") }
    }

    fun onForceRecycleClick(coin: Coin) = launchUnit {
        interactor.forceRecycle(coin)
            .onFailure { showMessage("Failed to recycle coin") }
    }

    fun onBackupUpdateClick() {
        interactor.startDeepSearch()
    }

    fun onBackupCloseClick() {
        interactor.markBackupCompleted()
    }

    fun openScanner() {
        router.openScan()
    }

    private fun BackupProgress.toBalanceRestoreUiState(): BalanceRestoreUiState {
        return when (this) {
            is BackupProgress.Deep.Completed,
            is BackupProgress.Deep.Syncing,
            is BackupProgress.Initial.Completed -> BalanceRestoreUiState.Restore(inProgress = isInProgress())
            else -> BalanceRestoreUiState.SendCash
        }
    }
}
