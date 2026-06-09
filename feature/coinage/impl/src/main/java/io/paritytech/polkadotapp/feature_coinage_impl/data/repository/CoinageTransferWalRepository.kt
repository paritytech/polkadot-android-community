package io.paritytech.polkadotapp.feature_coinage_impl.data.repository

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.database.dao.CoinageTransferWalDao
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.toDomainOrNull
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.toLocal
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.TransferWalEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface CoinageTransferWalRepository {
    suspend fun save(entry: TransferWalEntry): Result<Unit>

    suspend fun saveAll(entries: List<TransferWalEntry>): Result<Unit>

    suspend fun delete(id: String): Result<Unit>

    suspend fun getAllForChain(chainId: ChainId): Result<List<TransferWalEntry>>

    fun observeForChain(chainId: ChainId): Flow<List<TransferWalEntry>>
}

class RealCoinageTransferWalRepository @Inject constructor(
    private val dao: CoinageTransferWalDao,
) : CoinageTransferWalRepository {
    override suspend fun save(entry: TransferWalEntry): Result<Unit> = runCatching {
        dao.insert(entry.toLocal())
    }

    override suspend fun saveAll(entries: List<TransferWalEntry>): Result<Unit> = runCatching {
        dao.insertAll(entries.map { it.toLocal() })
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        dao.delete(id)
    }

    override suspend fun getAllForChain(chainId: ChainId): Result<List<TransferWalEntry>> = runCatching {
        dao.getAllForChain(chainId).mapNotNull { it.toDomainOrNull() }
    }

    override fun observeForChain(chainId: ChainId): Flow<List<TransferWalEntry>> =
        dao.observeForChain(chainId).map { locals -> locals.mapNotNull { it.toDomainOrNull() } }
}
