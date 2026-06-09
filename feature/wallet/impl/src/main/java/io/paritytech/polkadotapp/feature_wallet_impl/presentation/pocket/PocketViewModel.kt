package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_videogame_api.domain.collectibles.CollectiblesUrlResolver
import io.paritytech.polkadotapp.feature_wallet_impl.PocketRouter
import io.paritytech.polkadotapp.feature_wallet_impl.domain.interactor.PocketInteractor
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.PocketCardUiModel
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.PocketScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PocketViewModel @Inject constructor(
    interactor: PocketInteractor,
    private val tokenAmountMapper: TokenAmountMapper,
    private val router: PocketRouter,
    private val collectiblesUrlResolver: CollectiblesUrlResolver
) : BaseViewModel() {
    private val selectedCardId = MutableStateFlow<String?>(null)
    private val collectiblesShown = MutableStateFlow(false)

    private val cardsFlow = combine(
        interactor.observeDigitalDollarBalance(),
        interactor.observeUsername(),
        interactor.observeBackupProgress(),
        interactor.observeRank(),
        interactor.observeAddress()
    ) { balance, username, backupProgress, rank, address ->
        listOf(
            PocketCardUiModel.DigitalDollar(
                balance = tokenAmountMapper.mapFrom(balance.total),
                availableNow = tokenAmountMapper.mapFrom(balance.availableNow),
                syncInProgress = backupProgress.isInProgress()
            ),
            PocketCardUiModel.IdCard(username = username, address = address, rank = rank)
        )
    }

    val cards: StateFlow<List<PocketCardUiModel>> = cardsFlow.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val state: StateFlow<PocketScreenState> = combine(
        cardsFlow,
        selectedCardId,
        collectiblesShown
    ) { cards, selectedId, collectiblesShown ->
        val selectedCard = cards.firstOrNull { it.id == selectedId }
        when {
            selectedCard != null -> PocketScreenState.CardDetails(selectedCard = selectedCard)
            collectiblesShown -> PocketScreenState.Collectibles
            else -> {
                val collectiblesAvailable = collectiblesUrlResolver.resolveUrl() != null
                PocketScreenState.List(collectiblesAvailable = collectiblesAvailable)
            }
        }
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = PocketScreenState.List(collectiblesAvailable = false)
    )

    fun selectCard(card: PocketCardUiModel) {
        selectedCardId.value = card.id
    }

    fun dismissCard() {
        selectedCardId.value = null
    }

    fun showCollectiblesSketchbook() {
        collectiblesShown.value = true
    }

    fun hideCollectiblesSketchbook() {
        collectiblesShown.value = false
    }

    fun openCollectibles() {
        router.openCollectibles()
    }

    fun openScanner() {
        router.openScan()
    }
}
