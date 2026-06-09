package io.paritytech.polkadotapp.feature_statement_store_impl.data.repository

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchAlias
import io.paritytech.polkadotapp.chains.di.RemoteSourceQualifier
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.withRuntime
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.common.utils.scale.BigEndianU32Scale
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_statement_store_impl.data.blockchain.liteStmtStoreSlotsPerPeriod
import io.paritytech.polkadotapp.feature_statement_store_impl.data.blockchain.model.StmtStoreAllowanceEntry
import io.paritytech.polkadotapp.feature_statement_store_impl.data.blockchain.statementStoreAllowances
import io.paritytech.polkadotapp.feature_statement_store_impl.data.blockchain.statementStoreResources
import io.paritytech.polkadotapp.feature_statement_store_impl.data.blockchain.stmtStoreReplacementCooldown
import io.paritytech.polkadotapp.feature_statement_store_impl.data.blockchain.stmtStoreSlotsPerPeriod
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RealStatementStoreSlotRepository @Inject constructor(
    @RemoteSourceQualifier private val storageDataSource: StorageDataSource,
    private val chainRegistry: ChainRegistry,
) : StatementStoreSlotRepository {
    override suspend fun maxSlotsPerPeriod(chainId: ChainId, collection: PeopleCollection): UInt {
        return chainRegistry.withRuntime(chainId) {
            when (collection) {
                PeopleCollection.People -> runtime.metadata.statementStoreResources.stmtStoreSlotsPerPeriod
                PeopleCollection.LitePeople -> runtime.metadata.statementStoreResources.liteStmtStoreSlotsPerPeriod
            }
        }
    }

    override suspend fun replacementCooldown(chainId: ChainId): Duration {
        val cooldownSeconds = chainRegistry.withRuntime(chainId) {
            runtime.metadata.statementStoreResources.stmtStoreReplacementCooldown
        }
        return cooldownSeconds.toLong().seconds
    }

    override suspend fun allowanceEntries(
        chainId: ChainId,
        period: UInt,
        candidates: Collection<BandersnatchAlias>,
    ): Map<BandersnatchAlias, StmtStoreAllowanceEntry> {
        if (candidates.isEmpty()) return emptyMap()
        return storageDataSource.query(chainId) {
            val periodKey = BigEndianU32Scale(period)
            val keys = candidates.map { periodKey to it }
            runtime.metadata.statementStoreResources.statementStoreAllowances.entries(keys)
                .mapKeys { (compositeKey, _) -> compositeKey.second }
        }
    }
}
