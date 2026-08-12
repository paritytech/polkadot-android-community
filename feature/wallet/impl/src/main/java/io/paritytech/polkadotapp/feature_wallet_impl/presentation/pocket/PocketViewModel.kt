package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.presentation.sharing.SharingManager
import io.paritytech.polkadotapp.common.utils.ContentSharing
import io.paritytech.polkadotapp.common.utils.flowOf
import io.paritytech.polkadotapp.common.utils.inBackground
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.stateInBackground
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.formatFiat
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.RoundPrecision
import io.paritytech.polkadotapp.feature_videogame_api.domain.collectibles.CollectiblesUrlResolver
import io.paritytech.polkadotapp.feature_wallet_impl.PocketRouter
import io.paritytech.polkadotapp.feature_wallet_impl.domain.interactor.PocketInteractor
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.PocketCardUiModel
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.PocketScreenState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PocketViewModel @Inject constructor(
    interactor: PocketInteractor,
    private val tokenAmountMapper: TokenAmountMapper,
    private val tokenAmountFormatter: TokenAmountFormatter,
    private val router: PocketRouter,
    private val collectiblesUrlResolver: CollectiblesUrlResolver,
    private val idShareImageRenderer: IdShareImageRenderer,
    private val sharingManager: SharingManager
) : BaseViewModel() {
    private val selectedCardId = MutableStateFlow<String?>(null)
    private val collectiblesShown = MutableStateFlow(false)

    val cards = combine(
        interactor.observeDigitalDollarBalance(),
        interactor.observeUsername(),
        interactor.observeBackupProgress(),
        interactor.observeRank(),
        interactor.observeAddress()
    ) { balance, username, backupProgress, rank, address ->
        persistentListOf(
            PocketCardUiModel.DigitalDollar(
                balance = tokenAmountMapper.mapFrom(balance.total),
                availableNow = tokenAmountMapper.mapFrom(balance.availableNow),
                syncInProgress = backupProgress.isInProgress()
            ),
            PocketCardUiModel.IdCard(username = username, address = address, rank = rank)
        )
    }
        .distinctUntilChangedBy { cards -> cards.map(::cardDisplayKey) }
        .inBackground()
        .stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = persistentListOf()
        )

    val collectiblesAvailable = flowOf {
        collectiblesUrlResolver.resolveUrl() != null
    }
        .stateInBackground(initialValue = false)

    val state: StateFlow<PocketScreenState> = combine(
        cards,
        selectedCardId,
        collectiblesShown,
        collectiblesAvailable
    ) { cards, selectedId, collectiblesShown, collectiblesAvailable ->
        val selectedCard = cards.firstOrNull { it.id == selectedId }
        when {
            selectedCard != null -> PocketScreenState.CardDetails(selectedCard = selectedCard)
            collectiblesShown -> PocketScreenState.Collectibles
            else -> PocketScreenState.List(collectiblesAvailable = collectiblesAvailable)
        }
    }
        .stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = PocketScreenState.List(collectiblesAvailable = false)
        )

    private fun cardDisplayKey(card: PocketCardUiModel): String = when (card) {
        is PocketCardUiModel.DigitalDollar -> listOf(
            tokenAmountFormatter.formatTokenAmount(card.balance, RoundPrecision.FIAT, withSymbol = false),
            tokenAmountFormatter.formatFiat(card.availableNow),
            card.syncInProgress,
            card.notFullyAvailable
        ).joinToString("|")

        is PocketCardUiModel.IdCard -> listOf(card.username, card.address, card.rank).joinToString("|")
    }

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

    fun onShareId() = launchUnit {
        val idCard = cards.value.filterIsInstance<PocketCardUiModel.IdCard>().firstOrNull() ?: return@launchUnit
        val text = "${idCard.username}\n${idCard.address}"

        idShareImageRenderer.render(idCard.username, idCard.address)
            .logFailure("PocketViewModel: failed to render ID share image")
            .onSuccess { uri ->
                sharingManager.shareContent(
                    ContentSharing.file(
                        text = text,
                        uri = uri,
                        mimeType = "image/jpeg"
                    )
                )
            }
            .onFailure { sharingManager.shareText(text) }
    }
}
