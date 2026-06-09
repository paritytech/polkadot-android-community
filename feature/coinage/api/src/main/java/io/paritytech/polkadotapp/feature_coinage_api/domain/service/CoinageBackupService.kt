package io.paritytech.polkadotapp.feature_coinage_api.domain.service

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.BackupProgress
import kotlinx.coroutines.flow.Flow

interface CoinageBackupService {
    fun subscribeProgress(): Flow<BackupProgress>

    context(ComputationalScope)
    fun start()

    context(ComputationalScope)
    fun deepSearch()

    context(ComputationalScope)
    fun markAsCompleted()
}
