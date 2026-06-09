package io.paritytech.polkadotapp.feature_videogame_api.domain.state

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_videogame_api.domain.state.model.VideoGamesProgress
import kotlinx.coroutines.flow.Flow

interface VideoGamesProgressUseCase {
    context(ComputationalScope)
    fun videoGamesProgressFlow(): Flow<VideoGamesProgress>

    context(ComputationalScope)
    suspend fun videoGameProgress(): VideoGamesProgress
}
