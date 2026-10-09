package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.paritytech.polkadotapp.common.presentation.loading.dataOrNull
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.presentation.sharing.SharingManager
import io.paritytech.polkadotapp.common.utils.ContentSharing
import io.paritytech.polkadotapp.common.utils.inBackground
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.withLoading
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.BackupProgress
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.TokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.mapper.TokenAmountMapper
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.RoundPrecision
import io.paritytech.polkadotapp.feature_wallet_impl.PocketRouter
import io.paritytech.polkadotapp.feature_wallet_impl.domain.interactor.PocketInteractor
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.PocketCardUiModel
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models.PocketScreenState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import io.paritytech.polkadotapp.common.R as RCommon

@HiltViewModel
class PocketViewModel @Inject constructor(
    private val interactor: PocketInteractor,
    private val tokenAmountMapper: TokenAmountMapper,
    private val tokenAmountFormatter: TokenAmountFormatter,
    private val router: PocketRouter,
    private val idShareImageRenderer: IdShareImageRenderer,
    private val sharingManager: SharingManager,
    @param:ApplicationContext private val context: Context
) : BaseViewModel() {
    private val selectedCardId = MutableStateFlow<String?>(null)

    private val digitalDollarAmounts = interactor.observeDigitalDollarBalance()
        .map { balance ->
            PocketCardUiModel.DigitalDollar.Amounts(
                balance = tokenAmountMapper.mapFrom(balance.total),
                ready = tokenAmountMapper.mapFrom(balance.ready)
            )
        }
        .withLoading("PocketViewModel: Failed to observe digital dollar balance")

    // Both upstreams start unresolved so the card is on screen from the first frame, shimmering its
    // amount, instead of appearing only once a balance arrives.
    private val balanceCard = combine(
        digitalDollarAmounts,
        interactor.observeBackupProgress().onStart { emit(BackupProgress.Unknown) },
        interactor.observeAccountBackupPending().onStart { emit(false) },
    ) { amounts, backupProgress, accountBackupPending ->
        PocketCardUiModel.DigitalDollar(
            amounts = amounts,
            syncInProgress = backupProgress.isInProgress(),
            accountBackupPending = accountBackupPending,
        )
    }

    private val addressCard = combine<_, _, PocketCardUiModel.IdCard?>(
        interactor.observeUsername(),
        interactor.observeAddress()
    ) { username, address ->
        PocketCardUiModel.IdCard(username = username, address = address)
    }.onStart { emit(null) }

    val cards = combine(balanceCard, addressCard) { balance, address ->
        listOfNotNull(balance, address).toImmutableList()
    }
        .distinctUntilChangedBy { cards -> cards.map(::cardDisplayKey) }
        .inBackground()
        .stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = persistentListOf()
        )

    val state: StateFlow<PocketScreenState> = combine(
        cards,
        selectedCardId
    ) { cards, selectedId ->
        val selectedCard = cards.firstOrNull { it.id == selectedId }
        if (selectedCard != null) {
            PocketScreenState.CardDetails(selectedCard = selectedCard)
        } else {
            PocketScreenState.List
        }
    }
        .stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = PocketScreenState.List
        )

    private fun cardDisplayKey(card: PocketCardUiModel): String = when (card) {
        is PocketCardUiModel.DigitalDollar -> {
            val amounts = card.amounts.dataOrNull

            listOf(
                amounts?.let {
                    tokenAmountFormatter.formatTokenAmount(it.balance, RoundPrecision.FIAT, withSymbol = false)
                },
                amounts?.let { tokenAmountFormatter.formatTokenAmount(it.ready, RoundPrecision.FIAT, withSymbol = false) },
                card.syncInProgress,
                card.accountBackupPending,
                amounts?.notFullyReady
            ).joinToString("|")
        }

        is PocketCardUiModel.IdCard -> listOf(card.username, card.address).joinToString("|")
    }

    fun selectCard(card: PocketCardUiModel) {
        selectedCardId.value = card.id
    }

    fun dismissCard() {
        selectedCardId.value = null
    }

    fun onShareId() = launchUnit {
        val idCard = cards.value.filterIsInstance<PocketCardUiModel.IdCard>().firstOrNull() ?: return@launchUnit

        interactor.getAppSharingUrl()
            .onSuccess { url -> shareId(idCard, url) }
            .onFailure { showPresentationError(ShareIdFailedPresentationError(it)) }
    }

    private suspend fun shareId(idCard: PocketCardUiModel.IdCard, appSharingUrl: String) {
        val text = context.getString(RCommon.string.pocket_id_share_message, appSharingUrl, idCard.username)

        idShareImageRenderer.render(idCard.address)
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
