package io.paritytech.polkadotapp.feature_statement_store_impl.data.blockchain

import io.novasama.substrate_sdk_android.runtime.metadata.RuntimeMetadata
import io.novasama.substrate_sdk_android.runtime.metadata.module.Module
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchAlias
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableModule
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableStorageEntry2
import io.paritytech.polkadotapp.chains.storage.source.query.api.constant
import io.paritytech.polkadotapp.chains.storage.source.query.api.storage2
import io.paritytech.polkadotapp.chains.util.WithRuntime
import io.paritytech.polkadotapp.chains.util.resources
import io.paritytech.polkadotapp.common.utils.scale.BigEndianU32Scale
import io.paritytech.polkadotapp.feature_statement_store_impl.data.blockchain.model.StmtStoreAllowanceEntry

@JvmInline
value class StatementStoreResourcesApi(override val module: Module) : QueryableModule

context(WithRuntime)
val RuntimeMetadata.statementStoreResources: StatementStoreResourcesApi
    get() = StatementStoreResourcesApi(resources())

context(WithRuntime)
val StatementStoreResourcesApi.stmtStoreSlotsPerPeriod: UInt
    get() = constant("StmtStoreSlotsPerPeriod")

context(WithRuntime)
val StatementStoreResourcesApi.liteStmtStoreSlotsPerPeriod: UInt
    get() = constant("LiteStmtStoreSlotsPerPeriod")

context(WithRuntime)
val StatementStoreResourcesApi.stmtStoreReplacementCooldown: UInt
    get() = constant("StmtStoreReplacementCooldown")

context(WithRuntime)
val StatementStoreResourcesApi.statementStoreAllowances: QueryableStorageEntry2<BigEndianU32Scale, BandersnatchAlias, StmtStoreAllowanceEntry>
    get() = storage2("StatementStoreAllowances")
