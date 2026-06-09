package io.paritytech.polkadotapp.feature_videogame_impl.presentation.voting

import io.paritytech.polkadotapp.feature_videogame_impl.presentation.voting.models.PlayerVotingUiModel
import kotlinx.coroutines.flow.StateFlow

interface VideoGameVotingContract {
    val players: StateFlow<List<PlayerVotingUiModel>>
    val inProgress: StateFlow<Boolean>
    val autoConfirm: StateFlow<Boolean>

    fun togglePlayerVote(player: PlayerVotingUiModel)
    fun confirm()
}
