@file:OptIn(ExperimentalTime::class)

package io.paritytech.polkadotapp.feature_statement_store_impl.domain.slotAllocator

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.common.utils.mapAsync
import io.paritytech.polkadotapp.feature_people_api.domain.BandersnatchKeyResolver
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementSlotsForCollection
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlot
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlots
import io.paritytech.polkadotapp.feature_statement_store_impl.data.extension.statementStoreSlot
import io.paritytech.polkadotapp.feature_statement_store_impl.data.repository.StatementStoreSlotRepository
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class StatementStoreSlotLoader @Inject constructor(
    private val statementStoreSlotRepository: StatementStoreSlotRepository,
    private val bandersnatchKeyResolver: BandersnatchKeyResolver,
) {
    suspend fun loadSlots(context: AllocateContext): Result<StatementStoreSlots> {
        return runCatching {
            val perCollection = context.availableCollections.mapAsync { collection ->
                loadSlotsForCollection(context, collection)
            }
            StatementStoreSlots(perCollection)
        }
    }

    private suspend fun loadSlotsForCollection(
        context: AllocateContext,
        collection: PeopleCollection,
    ): StatementSlotsForCollection {
        val maxSlots = statementStoreSlotRepository.maxSlotsPerPeriod(context.chain.id, collection)
        val aliasesByIndex = (0u until maxSlots).associateWith { seq ->
            val ctx = BandersnatchContext.statementStoreSlot(context.period, seq)
            bandersnatchKeyResolver.getAliasInContext(collection, ctx)
        }
        val taken = statementStoreSlotRepository.allowanceEntries(context.chain.id, context.period, aliasesByIndex.values)
        val slots = aliasesByIndex.map { (seq, alias) ->
            val entry = taken[alias]
            if (entry == null) {
                StatementStoreSlot.Free(seq)
            } else {
                StatementStoreSlot.Taken(
                    seq = seq,
                    accountId = entry.accountId,
                    since = Instant.fromEpochSeconds(entry.since.toLong()),
                )
            }
        }
        return StatementSlotsForCollection(collection, slots)
    }
}
