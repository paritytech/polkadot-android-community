package io.paritytech.polkadotapp.feature_settings_impl.presentation.forceReclaim

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.withAmount
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ForceReclaimCoinsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ReclaimOutcome
import io.paritytech.polkadotapp.feature_settings_impl.SettingsRouter
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForceReclaimViewModel @Inject constructor(
    private val forceReclaimCoinsUseCase: ForceReclaimCoinsUseCase,
    private val tokenAmountMapper: TokenAmountMapper,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val router: SettingsRouter,
) : BaseViewModel(), ForceReclaimContract {
    private val isReclaiming = MutableStateFlow(false)

    override val state: StateFlow<ForceReclaimUiState> = isReclaiming
        .map { ForceReclaimUiState(isReclaiming = it) }
        .stateIn(this, SharingStarted.Eagerly, ForceReclaimUiState(isReclaiming = false))

    override val reclaimEvents = MutableSharedFlow<ForceReclaimEvent>()

    override fun onReclaimClick() {
        if (isReclaiming.value) return

        launch {
            isReclaiming.value = true
            forceReclaimCoinsUseCase()
                .logFailure("Failed to force reclaim coins")
                .onSuccess { reclaimEvents.emit(it.toEvent()) }
                .onFailure { reclaimEvents.emit(ForceReclaimEvent.Error) }
            isReclaiming.value = false
        }
    }

    override fun onBackClick() {
        router.back()
    }

    private suspend fun ReclaimOutcome.toEvent(): ForceReclaimEvent = when (this) {
        ReclaimOutcome.NothingToReclaim -> ForceReclaimEvent.NothingToReclaim
        is ReclaimOutcome.Reclaimed -> ForceReclaimEvent.Reclaimed(mapReclaimed(amount))
    }

    private suspend fun mapReclaimed(amount: Balance): TokenAmountModel {
        return tokenAmountMapper.mapFrom(chainAssetProvider.asset().withAmount(amount))
    }
}
