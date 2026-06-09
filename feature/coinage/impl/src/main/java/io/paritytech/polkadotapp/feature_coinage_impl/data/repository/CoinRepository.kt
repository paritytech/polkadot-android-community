package io.paritytech.polkadotapp.feature_coinage_impl.data.repository

import io.paritytech.polkadotapp.chains.di.RemoteSourceQualifier
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.withRuntime
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.chains.storage.source.query.metadata
import io.paritytech.polkadotapp.chains.storage.source.queryCatching
import io.paritytech.polkadotapp.chains.storage.source.subscribeCatching
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.utils.mapList
import io.paritytech.polkadotapp.database.dao.CoinDao
import io.paritytech.polkadotapp.database.dao.CoinUpdateLocal
import io.paritytech.polkadotapp.database.model.CoinLocal
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinUpdate
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.coinage
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.coinsByOwner
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.maxConsolidation
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.domain.common.getNextIndex
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

private const val MAX_AGE = 16
private const val RECYCLING_AGE_OFFSET = 2

interface CoinRepository {
    suspend fun save(coin: Coin)

    suspend fun saveAll(coins: List<Coin>)

    fun subscribeAllCoins(): Flow<List<Coin>>

    fun subscribeAllCoinsWithUnknownAge(): Flow<List<Coin>>

    fun subscribeAllNotSpentCoins(): Flow<List<Coin>>

    suspend fun getAllNotSpentCoins(): List<Coin>

    suspend fun getCoinsWithSpentState(state: Coin.SpentState): List<Coin>

    fun subscribeCoinsExcludingSpentOnChain(): Flow<List<Coin>>

    fun getCoinRecyclingAge(): Int

    fun getMaxCoinAge(): Int

    suspend fun getNextDerivationIndex(): Int

    suspend fun subscribeCoinsInfoFor(chainId: ChainId, accounts: List<AccountId>): Flow<Result<Map<AccountId, OnChainCoinInfo?>>>

    suspend fun fetchCoinsInfoFor(chainId: ChainId, accounts: List<AccountId>): Result<Map<AccountId, OnChainCoinInfo?>>

    suspend fun updateCoins(updates: List<CoinUpdate>)

    suspend fun getActiveCoins(): List<Coin>

    suspend fun getActiveCoinsWithKnownAge(minAge: Int): List<Coin>

    fun subscribeActiveCoins(): Flow<List<Coin>>

    suspend fun fetchMaxConsolidation(chainId: ChainId): Result<Int>

    suspend fun removeCoin(index: Int)

    suspend fun removeCoins(indices: List<Int>)

    suspend fun setSpentStateByDerivationIndices(indices: List<Int>, state: Coin.SpentState)
}

class RealCoinRepository @Inject constructor(
    private val coinDao: CoinDao,
    private val chainRegistry: ChainRegistry,
    @param:RemoteSourceQualifier private val remoteStorageSource: StorageDataSource
) : CoinRepository {
    override suspend fun save(coin: Coin) {
        coinDao.insert(coin.toLocal())
    }

    override suspend fun saveAll(coins: List<Coin>) {
        coinDao.insertAll(coins.map { it.toLocal() })
    }

    override fun subscribeAllCoins(): Flow<List<Coin>> {
        return coinDao.subscribeAll().mapList { it.toDomain() }
    }

    override fun subscribeAllNotSpentCoins(): Flow<List<Coin>> =
        coinDao.subscribeCoinsWithSpentState(CoinLocal.SpentState.NOT_SPENT)
            .mapList { it.toDomain() }

    override suspend fun getAllNotSpentCoins(): List<Coin> =
        coinDao.getCoinsWithSpentState(CoinLocal.SpentState.NOT_SPENT)
            .map { it.toDomain() }

    override suspend fun getCoinsWithSpentState(state: Coin.SpentState): List<Coin> =
        coinDao.getCoinsWithSpentState(state.toLocal())
            .map { it.toDomain() }

    override fun subscribeCoinsExcludingSpentOnChain(): Flow<List<Coin>> =
        coinDao.subscribeCoinsExcludingSpentOnChain(CoinLocal.SpentState.SPENT_ON_CHAIN)
            .mapList { it.toDomain() }

    override fun getMaxCoinAge(): Int {
        return MAX_AGE
    }

    override fun getCoinRecyclingAge(): Int {
        return getMaxCoinAge() - RECYCLING_AGE_OFFSET
    }

    override fun subscribeAllCoinsWithUnknownAge(): Flow<List<Coin>> {
        return coinDao.subscribeAllCoinsWithUnknownAge().mapList { it.toDomain() }
    }

    override suspend fun getNextDerivationIndex(): Int {
        return coinDao.getMaxDerivationIndex().getNextIndex()
    }

    override suspend fun fetchCoinsInfoFor(chainId: ChainId, accounts: List<AccountId>): Result<Map<AccountId, OnChainCoinInfo?>> {
        return remoteStorageSource.queryCatching(chainId) {
            metadata.coinage.coinsByOwner.entries(accounts)
        }
    }

    override suspend fun subscribeCoinsInfoFor(chainId: ChainId, accounts: List<AccountId>): Flow<Result<Map<AccountId, OnChainCoinInfo?>>> {
        return remoteStorageSource.subscribeCatching(chainId) {
            metadata.coinage.coinsByOwner.observe(accounts)
        }
    }

    override suspend fun fetchMaxConsolidation(chainId: ChainId): Result<Int> {
        return runCatching {
            chainRegistry.withRuntime(chainId) {
                runtime.metadata.coinage.maxConsolidation
            }
        }
    }

    override suspend fun updateCoins(updates: List<CoinUpdate>) {
        val updateLocals = updates.map { it.toLocal() }
        coinDao.updateCoins(updateLocals)
    }

    override suspend fun getActiveCoins(): List<Coin> {
        return coinDao.getAllAgedCoinsWithState(CoinLocal.SpentState.NOT_SPENT)
            .map { it.toDomain() }
    }

    override suspend fun getActiveCoinsWithKnownAge(minAge: Int): List<Coin> {
        return coinDao.getCoinsWithKnownAgeAtLeast(CoinLocal.SpentState.NOT_SPENT, minAge)
            .map { it.toDomain() }
    }

    override fun subscribeActiveCoins(): Flow<List<Coin>> {
        return coinDao.subscribeAllAgedCoinsWithState(CoinLocal.SpentState.NOT_SPENT)
            .mapList { it.toDomain() }
    }

    override suspend fun removeCoin(index: Int) {
        coinDao.removeCoin(index)
    }

    override suspend fun removeCoins(indices: List<Int>) {
        if (indices.isEmpty()) return

        coinDao.removeCoins(indices)
    }

    override suspend fun setSpentStateByDerivationIndices(indices: List<Int>, state: Coin.SpentState) {
        if (indices.isEmpty()) return

        coinDao.setSpentStateByDerivationIndices(indices, state.toLocal())
    }

    fun CoinLocal.toDomain(): Coin {
        return Coin(
            derivationIndex = derivationIndex,
            valueExponent = ValueExponent(valueExponent),
            age = ageValue?.let(Coin.Age::Known) ?: Coin.Age.Unknown,
            spentState = spentState.toDomain(),
            accountId = accountId.intoAccountId()
        )
    }

    private fun CoinLocal.SpentState.toDomain(): Coin.SpentState = when (this) {
        CoinLocal.SpentState.SPENT_LOCALLY -> Coin.SpentState.SPENT_LOCALLY
        CoinLocal.SpentState.SPENT_ON_CHAIN -> Coin.SpentState.SPENT_ON_CHAIN
        CoinLocal.SpentState.NOT_SPENT -> Coin.SpentState.NOT_SPENT
    }

    fun Coin.toLocal(): CoinLocal {
        return CoinLocal(
            derivationIndex = derivationIndex,
            accountId = accountId.value,
            valueExponent = valueExponent.value,
            ageValue = (age as? Coin.Age.Known)?.value,
            spentState = spentState.toLocal()
        )
    }

    private fun Coin.SpentState.toLocal(): CoinLocal.SpentState = when (this) {
        Coin.SpentState.SPENT_LOCALLY -> CoinLocal.SpentState.SPENT_LOCALLY
        Coin.SpentState.SPENT_ON_CHAIN -> CoinLocal.SpentState.SPENT_ON_CHAIN
        Coin.SpentState.NOT_SPENT -> CoinLocal.SpentState.NOT_SPENT
    }

    private fun CoinUpdate.toLocal() = CoinUpdateLocal(
        accountId = accountId,
        age = age,
        spentState = spentState.toLocal()
    )
}
