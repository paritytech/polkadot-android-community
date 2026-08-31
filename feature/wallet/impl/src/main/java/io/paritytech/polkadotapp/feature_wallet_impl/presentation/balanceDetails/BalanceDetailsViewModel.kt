package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.withAmount
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.domain.BalanceDetailsInteractor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BalanceDetailsViewModel @Inject constructor(
    interactor: BalanceDetailsInteractor,
    private val tokenAmountMapper: TokenAmountMapper,
) : BaseViewModel() {
    val state: StateFlow<LoadingState<BalanceDetailsUiState>> = interactor.observeBalance()
        .map { breakdown ->
            with(breakdown) {
                LoadingState.Loaded(
                    BalanceDetailsUiState(
                        availablePrivate = tokenAmountMapper.mapFrom(asset.withAmount(availablePrivate)),
                        exposed = tokenAmountMapper.mapFrom(asset.withAmount(exposed)),
                        canSpendExposed = canSpendExposed,
                        notAvailable = tokenAmountMapper.mapFrom(asset.withAmount(notAvailable)),
                    )
                )
            }
        }
        .stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = LoadingState.Loading
        )
}
