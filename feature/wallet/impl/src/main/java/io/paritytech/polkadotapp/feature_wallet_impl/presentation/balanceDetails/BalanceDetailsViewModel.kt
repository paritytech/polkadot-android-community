package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.withAmount
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
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
                        availablePrivate = availablePrivate.toModelOrNull(asset),
                        exposed = exposed.toModelOrNull(asset),
                        canSpendExposed = canSpendExposed,
                        notAvailable = notAvailable.toModelOrNull(asset),
                    )
                )
            }
        }
        .stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = LoadingState.Loading
        )

    /** Nothing in a bucket is not a fact worth a row, so an empty one drops out here. */
    private fun Balance.toModelOrNull(asset: Chain.Asset): TokenAmountModel? = takeIf { !it.isZero() }
        ?.let { tokenAmountMapper.mapFrom(asset.withAmount(it)) }
}
