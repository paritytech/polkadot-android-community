package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models

sealed interface PocketScreenState {
    val contentKey: String

    data object List : PocketScreenState {
        override val contentKey: String get() = "list"
    }

    data class CardDetails(
        val selectedCard: PocketCardUiModel
    ) : PocketScreenState {
        override val contentKey: String get() = selectedCard.id
    }
}
