package io.paritytech.polkadotapp.feature_coinage_impl.domain.service

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.utils.getOrEmpty
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.recyclerLocationOrThrow
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.toStorageKey
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.CoinageInstanceIdProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersRepository
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class VoucherRingMembersService @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val membersRepository: MembersRepository,
    private val voucherRepository: VoucherRepository,
    private val coinageInstanceIdProvider: CoinageInstanceIdProvider
) {
    companion object {
        private const val MIN_RING_MEMBERS = 10
    }

    context(scope: ComputationalScope)
    suspend fun start() {
        val chainId = chainAssetProvider.chainId()
        val instanceId = coinageInstanceIdProvider.instanceId()
            .logFailure("Can't resolve coinage instance id")
            .getOrNull() ?: return

        voucherRepository.subscribeVouchersInRecycler()
            .map {
                it.groupBy { voucher ->
                    val location = voucher.recyclerLocationOrThrow()
                    RecyclerKey(voucher.recyclerValue, location.recyclerIndex)
                }
            }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { vouchersByRecyclerKey ->
                val storageKeys = vouchersByRecyclerKey.keys.map { it.toStorageKey(instanceId) }
                membersRepository.subscribeRingStatuses(chainId, storageKeys)
                    .map { it.logFailure("Can't fetch ring statuses").getOrEmpty() }
                    .map { ringStatuses -> vouchersByRecyclerKey to ringStatuses }
            }
            .onEach { (vouchersByRecyclerKey, ringStatuses) ->
                val updates = buildMap {
                    vouchersByRecyclerKey.forEach { (recyclerKey, affectedVouchers) ->
                        val status = ringStatuses[recyclerKey.toStorageKey(instanceId)]
                        val hasEnough = status != null && status.included >= MIN_RING_MEMBERS
                        affectedVouchers.forEach { voucher ->
                            if (hasEnough) {
                                put(voucher.ringVrfKeyIndex, true)
                            }
                        }
                    }
                }
                if (updates.isNotEmpty()) {
                    voucherRepository.updateRingMemberStatuses(updates)
                }
            }
            .launchIn(scope)
    }
}
