package io.paritytech.polkadotapp.feature_statement_store_impl.domain.notificationAllocator

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchAlias
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flattenResult
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_api.domain.getTldRetrying
import io.paritytech.polkadotapp.feature_people_api.domain.BandersnatchKeyResolver
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_statement_store_impl.data.extension.notificationSlot
import io.paritytech.polkadotapp.feature_statement_store_impl.data.repository.NotificationSlotRepository
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.slotAllocator.AllocateContext
import javax.inject.Inject

/** Free notification slots of the current period: People before LitePeople, ascending seq within each. */
class NotificationSeqPicker @Inject constructor(
    private val notificationSlotRepository: NotificationSlotRepository,
    private val bandersnatchKeyResolver: BandersnatchKeyResolver,
    private val dotNsTldProvider: DotNsTldProvider,
    private val reservations: NotificationSeqReservations,
) {
    suspend fun freeSlots(context: AllocateContext): Result<List<NotificationSlot>> {
        return context.availableCollections
            .sortedBy { if (it == PeopleCollection.People) 0 else 1 }
            .map { collection -> freeSlotsIn(context, collection) }
            .flattenResult()
            .map { it.flatten() }
    }

    private suspend fun freeSlotsIn(context: AllocateContext, collection: PeopleCollection): Result<List<NotificationSlot>> {
        return notificationSlotRepository.highestSeqPerPeriod(collection).flatMap { highestSeq ->
            val aliasBySeq = aliasesBySeq(collection, context.period, highestSeq)

            notificationSlotRepository.registeredAliases(context.chain.id, aliasBySeq.values).map { registered ->
                val reserved = reservations.reservedIn(context.period)
                aliasBySeq.filterValues { it !in registered }.keys
                    .map { seq -> NotificationSlot(collection, context.period, seq) }
                    .filterNot { it in reserved }
            }
        }
    }

    private suspend fun aliasesBySeq(
        collection: PeopleCollection,
        period: UInt,
        highestSeq: UByte,
    ): Map<UByte, BandersnatchAlias> {
        val tld = dotNsTldProvider.getTldRetrying()

        return (0..highestSeq.toInt()).associate { index ->
            val seq = index.toUByte()
            seq to bandersnatchKeyResolver.getAliasInContext(collection, BandersnatchContext.notificationSlot(tld, period, seq))
        }
    }
}
