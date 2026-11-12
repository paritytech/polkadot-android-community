package io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine

import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatStateSyncUseCase
import io.paritytech.polkadotapp.feature_chats_api.domain.interactors.ChatStateSyncUseCase.SettledIncomingPayment
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.HandedOffCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageStateSyncUseCase
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.ChatIdScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.CoinageMessageStatusScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.CoinageMessageStatusUpdateScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.HandedOffCoinScale
import io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale.SyncEntityScale
import io.paritytech.polkadotapp.feature_device_sync_impl.domain.engine.ChatStateEntityCollector.Companion.NOTHING_SYNCED_CHECKPOINT
import javax.inject.Inject
import kotlin.time.Instant

class CoinageStateEntityCollector @Inject constructor(
    private val coinageStateSyncUseCase: CoinageStateSyncUseCase,
    private val chatStateSyncUseCase: ChatStateSyncUseCase,
) {
    suspend fun collect(checkpoint: Instant): List<SyncEntityScale> {
        val changedAfter = checkpoint.takeIf { it != NOTHING_SYNCED_CHECKPOINT }
        val coins = coinageStateSyncUseCase.getHandedOffCoins(committedAfter = changedAfter)
        val payments = chatStateSyncUseCase.getSettledIncomingPayments(updatedAfter = changedAfter)

        return listOfNotNull(
            coins.map { it.toScale() }.takeIf { it.isNotEmpty() }?.let(SyncEntityScale::CoinsHandedOff),
            payments.map { it.toScale() }.takeIf { it.isNotEmpty() }?.let(SyncEntityScale::CoinageMessagesStatusUpdated),
        )
    }

    private fun SettledIncomingPayment.toScale(): CoinageMessageStatusUpdateScale {
        return CoinageMessageStatusUpdateScale(
            chatId = ChatIdScale.Contact(contactAccountId.value),
            messageId = messageId,
            status = when (val result = outcome) {
                is SettledIncomingPayment.Outcome.Claimed -> CoinageMessageStatusScale.Claimed(result.partial?.value)
                SettledIncomingPayment.Outcome.Failed -> CoinageMessageStatusScale.Failed
            },
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
