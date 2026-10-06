package io.paritytech.polkadotapp.feature_statement_store_impl.domain.notificationAllocator

import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seqs handed to claims built in this process but possibly not yet visible on chain, so that two claims built in
 * parallel never race for the same slot. A claim's reservation is replaced whenever it is built again.
 */
@Singleton
class NotificationSeqReservations @Inject constructor() {
    private val slotByClaim = ConcurrentHashMap<DurableTxId, NotificationSlot>()

    fun reserve(claim: DurableTxId, slot: NotificationSlot) {
        slotByClaim.values.removeIf { it.period < slot.period }
        slotByClaim[claim] = slot
    }

    fun release(claim: DurableTxId) {
        slotByClaim.remove(claim)
    }

    fun reservedIn(period: UInt): Set<NotificationSlot> =
        slotByClaim.values.filterTo(mutableSetOf()) { it.period == period }
}
