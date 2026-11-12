package io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.HandedOffCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageStateSyncUseCase
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.HandedOffCoinScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.ChatStateEntityCollector.Companion.NOTHING_SYNCED_CHECKPOINT
import javax.inject.Inject
import kotlin.time.Instant

class CoinageStateEntityCollector @Inject constructor(
    private val coinageStateSyncUseCase: CoinageStateSyncUseCase,
) {
    suspend fun collect(checkpoint: Instant): List<SyncEntityScale> {
        val coins = coinageStateSyncUseCase.getHandedOffCoins(committedAfter = checkpoint.takeIf { it != NOTHING_SYNCED_CHECKPOINT })

        return listOfNotNull(
            coins.map { it.toScale() }.takeIf { it.isNotEmpty() }?.let(SyncEntityScale::CoinsHandedOff),
        )
    }

    private fun HandedOffCoin.toScale(): HandedOffCoinScale {
        return HandedOffCoinScale(
            installationId = index.installation.value.value,
            derivationIndex = index.item.toULong(),
            exponent = exponent.toShort(),
        )
    }
}
