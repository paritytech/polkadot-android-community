package io.paritytech.polkadotapp.feature_coinage_impl.data.repository

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchPublicKey
import io.paritytech.polkadotapp.chains.di.RemoteSourceQualifier
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.chains.storage.source.query.metadata
import io.paritytech.polkadotapp.chains.storage.source.queryCatching
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.mapList
import io.paritytech.polkadotapp.database.dao.RecyclerVoucherDao
import io.paritytech.polkadotapp.database.dao.RecyclerVoucherLocationUpdate
import io.paritytech.polkadotapp.database.dao.RingMemberStatusUpdate
import io.paritytech.polkadotapp.database.model.RecyclerVoucherLocal
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.DerivationIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.filterReadyNowSecured
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.coinage
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.recyclersCoinToRecycler
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.recyclersUnloaded
import io.paritytech.polkadotapp.feature_coinage_impl.domain.common.getNextIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import javax.inject.Inject

interface VoucherRepository {
    suspend fun save(voucher: RecyclerVoucher)

    fun subscribeAllVouchers(): Flow<List<RecyclerVoucher>>

    fun subscribeVouchersNotInRecycler(): Flow<List<RecyclerVoucher>>

    suspend fun updateLocations(locations: Map<BandersnatchPublicKey, RecyclerVoucher.Location.InRecycler>)

    suspend fun getNextDerivationIndex(): DerivationIndex

    suspend fun saveAll(vouchers: List<RecyclerVoucher>)

    suspend fun getActiveVouchers(): List<RecyclerVoucher>

    suspend fun getAllNotUsedVouchers(): List<RecyclerVoucher>

    fun subscribeAllNotUsedVouchers(): Flow<List<RecyclerVoucher>>

    suspend fun updateRingMemberStatuses(updates: Map<Int, Boolean>)

    suspend fun fetchValuesForKeys(chainId: ChainId, voucherKeys: List<BandersnatchPublicKey>): Result<Map<BandersnatchPublicKey, ValueExponent>>

    suspend fun removeVoucher(index: DerivationIndex)

    suspend fun removeVouchers(indexes: List<DerivationIndex>)

    suspend fun getByRingVrfKeyIndices(indices: List<DerivationIndex>): List<RecyclerVoucher>

    suspend fun setUsageStateByRingVrfKeyIndices(indices: List<DerivationIndex>, state: RecyclerVoucher.UsageState)

    fun subscribeActiveVouchers(): Flow<List<RecyclerVoucher>>

    suspend fun detektNotUnloadedVouchers(
        chainId: ChainId,
        keys: List<Triple<BigInteger, BigInteger, ByteArray>>
    ): Result<Map<String, Unit?>>
}

fun VoucherRepository.subscribeVouchersAvailableNow(): Flow<List<RecyclerVoucher>> {
    return subscribeActiveVouchers().map { vouchers -> vouchers.filterReadyNowSecured() }
}

class RealVoucherRepository @Inject constructor(
    private val recyclerVoucherDao: RecyclerVoucherDao,
    @param:RemoteSourceQualifier private val remoteStorageSource: StorageDataSource
) : VoucherRepository {
    override suspend fun save(voucher: RecyclerVoucher) {
        recyclerVoucherDao.insert(voucher.toLocal())
    }

    override suspend fun saveAll(vouchers: List<RecyclerVoucher>) {
        recyclerVoucherDao.insertAll(vouchers.map { it.toLocal() })
    }

    override fun subscribeAllVouchers(): Flow<List<RecyclerVoucher>> {
        return recyclerVoucherDao.subscribeAll().mapList { it.toDomain() }
    }

    override fun subscribeVouchersNotInRecycler(): Flow<List<RecyclerVoucher>> {
        return recyclerVoucherDao.subscribeNotInRecycler().mapList { it.toDomain() }
    }

    override suspend fun updateLocations(locations: Map<BandersnatchPublicKey, RecyclerVoucher.Location.InRecycler>) {
        val updates = locations.map { (publicKey, location) ->
            RecyclerVoucherLocationUpdate(
                ringVrfPublicKey = publicKey.value,
                recyclerIndex = location.recyclerIndex.value.toInt()
            )
        }
        recyclerVoucherDao.updateLocations(updates)
    }

    override suspend fun removeVoucher(index: DerivationIndex) {
        recyclerVoucherDao.removeVoucher(index)
    }

    override suspend fun removeVouchers(indexes: List<DerivationIndex>) {
        recyclerVoucherDao.removeVouchers(indexes)
    }

    override suspend fun getByRingVrfKeyIndices(indices: List<DerivationIndex>): List<RecyclerVoucher> {
        return recyclerVoucherDao.getByRingVrfKeyIndices(indices).map { it.toDomain() }
    }

    override suspend fun setUsageStateByRingVrfKeyIndices(
        indices: List<DerivationIndex>,
        state: RecyclerVoucher.UsageState,
    ) {
        recyclerVoucherDao.setUsageStateByRingVrfKeyIndices(indices, state.toLocal())
    }

    override suspend fun getNextDerivationIndex(): DerivationIndex {
        return recyclerVoucherDao.getMaxRingVrfKeyIndex().getNextIndex()
    }

    override suspend fun detektNotUnloadedVouchers(
        chainId: ChainId,
        keys: List<Triple<BigInteger, BigInteger, ByteArray>>
    ): Result<Map<String, Unit?>> {
        return remoteStorageSource.queryCatching(chainId) {
            metadata.coinage.recyclersUnloaded.entries(keys)
        }
            .map {
                it.mapKeys { (key, _) -> key.third.toDataByteArray().toString() }
            }
    }

    override suspend fun getActiveVouchers(): List<RecyclerVoucher> {
        return recyclerVoucherDao.getVouchersInRecyclerByUsageState(RecyclerVoucherLocal.UsageState.NOT_USED)
            .map { it.toDomain() }
    }

    override suspend fun getAllNotUsedVouchers(): List<RecyclerVoucher> {
        return recyclerVoucherDao.getAllVouchersByUsageState(RecyclerVoucherLocal.UsageState.NOT_USED).map { it.toDomain() }
    }

    override fun subscribeAllNotUsedVouchers(): Flow<List<RecyclerVoucher>> {
        return recyclerVoucherDao.subscribeAllVouchersByUsageState(RecyclerVoucherLocal.UsageState.NOT_USED).mapList { it.toDomain() }
    }

    override suspend fun updateRingMemberStatuses(updates: Map<Int, Boolean>) {
        val daoUpdates = updates.map { (ringVrfKeyIndex, hasEnough) ->
            RingMemberStatusUpdate(ringVrfKeyIndex, hasEnough)
        }
        recyclerVoucherDao.updateRingMemberStatuses(daoUpdates)
    }

    override fun subscribeActiveVouchers(): Flow<List<RecyclerVoucher>> {
        return recyclerVoucherDao.subscribeVouchersInRecyclerByUsageState(RecyclerVoucherLocal.UsageState.NOT_USED)
            .mapList { it.toDomain() }
    }

    override suspend fun fetchValuesForKeys(chainId: ChainId, voucherKeys: List<BandersnatchPublicKey>): Result<Map<BandersnatchPublicKey, ValueExponent>> {
        return remoteStorageSource.queryCatching(chainId) {
            metadata.coinage.recyclersCoinToRecycler.entries(voucherKeys)
        }
            .map { it.mapValues { (_, value) -> ValueExponent(value.toInt()) } }
    }

    private fun RecyclerVoucherLocal.toDomain(): RecyclerVoucher {
        return RecyclerVoucher(
            ringVrfKeyIndex = ringVrfKeyIndex,
            ringVrfPublicKey = ringVrfPublicKey.toDataByteArray(),
            recyclerValue = ValueExponent(recyclerValue),
            location = toDomainLocation(),
            allocatedAt = allocatedAt,
            delayUnloadUntil = delayUnloadUntil,
            ringHasEnoughRingMembersToWithdraw = ringHasEnoughRingMembersToWithdraw,
            usageState = usageState.toDomain()
        )
    }

    private fun RecyclerVoucherLocal.toDomainLocation(): RecyclerVoucher.Location {
        val index = locationRecyclerIndex ?: return RecyclerVoucher.Location.Unknown
        return RecyclerVoucher.Location.InRecycler(recyclerIndex = RecyclerIndex(index.toBigInteger()))
    }

    private fun RecyclerVoucher.toLocal(): RecyclerVoucherLocal {
        val inRecycler = location as? RecyclerVoucher.Location.InRecycler
        return RecyclerVoucherLocal(
            ringVrfKeyIndex = ringVrfKeyIndex,
            ringVrfPublicKey = ringVrfPublicKey.value,
            recyclerValue = recyclerValue.value,
            locationRecyclerIndex = inRecycler?.recyclerIndex?.value?.toInt(),
            allocatedAt = allocatedAt,
            delayUnloadUntil = delayUnloadUntil,
            ringHasEnoughRingMembersToWithdraw = ringHasEnoughRingMembersToWithdraw,
            usageState = usageState.toLocal()
        )
    }

    private fun RecyclerVoucherLocal.UsageState.toDomain(): RecyclerVoucher.UsageState = when (this) {
        RecyclerVoucherLocal.UsageState.USED_LOCALLY -> RecyclerVoucher.UsageState.USED_LOCALLY
        RecyclerVoucherLocal.UsageState.USED_ON_CHAIN -> RecyclerVoucher.UsageState.USED_ON_CHAIN
        RecyclerVoucherLocal.UsageState.NOT_USED -> RecyclerVoucher.UsageState.NOT_USED
    }

    private fun RecyclerVoucher.UsageState.toLocal(): RecyclerVoucherLocal.UsageState = when (this) {
        RecyclerVoucher.UsageState.USED_LOCALLY -> RecyclerVoucherLocal.UsageState.USED_LOCALLY
        RecyclerVoucher.UsageState.USED_ON_CHAIN -> RecyclerVoucherLocal.UsageState.USED_ON_CHAIN
        RecyclerVoucher.UsageState.NOT_USED -> RecyclerVoucherLocal.UsageState.NOT_USED
    }
}
