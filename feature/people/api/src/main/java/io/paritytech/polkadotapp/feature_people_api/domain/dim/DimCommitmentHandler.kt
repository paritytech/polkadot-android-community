package io.paritytech.polkadotapp.feature_people_api.domain.dim

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import kotlinx.coroutines.flow.Flow

interface DimCommitmentHandler {
    val dimId: DimId

    val botId: String

    context(ComputationalScope)
    fun observeState(): Flow<DimState>

    context(ComputationalScope)
    suspend fun cancel(): Result<Unit>
}
