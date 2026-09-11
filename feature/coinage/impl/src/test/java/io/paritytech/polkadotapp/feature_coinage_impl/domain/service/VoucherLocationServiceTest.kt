package io.paritytech.polkadotapp.feature_coinage_impl.domain.service

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchPublicKey
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.toRingCollectionId
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.CoinageInstanceIdProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionIdWithIndex
import io.paritytech.polkadotapp.feature_members_api.data.model.RingPosition
import io.paritytech.polkadotapp.feature_members_api.data.model.RingStatus
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersRepository
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VoucherLocationServiceTest {
    @Test
    fun `timer starts at confirmed inclusion and restarts after restore`() = runTest {
        val chainAssetProvider = mock(ChainAssetProvider::class.java)
        val voucherRepository = mock(VoucherRepository::class.java)
        val membersRepository = mock(MembersRepository::class.java)
        val instanceIdProvider = mock(CoinageInstanceIdProvider::class.java)
        val timeProvider = mock(TimeProvider::class.java)
        val publicKey = byteArrayOf(7).toDataByteArray()
        val value = ValueExponent(3)
        val ringIndex = RecyclerIndex(2.toBigInteger())
        val collectionId = value.toRingCollectionId(0u)
        val ringKey = collectionId to ringIndex
        val statuses = MutableStateFlow<Result<Map<RingCollectionIdWithIndex, RingStatus?>>>(
            Result.success(mapOf(ringKey to RingStatus(total = 100, included = 31)))
        )
        val vouchers = MutableStateFlow(listOf(RecyclerVoucher(testKey(1), publicKey, value, RecyclerVoucher.Location.Unknown)))
        val updates = mutableListOf<Map<BandersnatchPublicKey, RecyclerVoucher.Location.InRecycler>>()
        var now = Instant.fromEpochMilliseconds(1_000L)
        whenever(chainAssetProvider.chainId()).thenReturn("chain")
        whenever(voucherRepository.subscribeAllVouchers()).thenReturn(vouchers)
        whenever(instanceIdProvider.instanceId()).thenReturn(Result.success(0u))
        whenever(membersRepository.subscribeMembers(any(), any(), any())).thenReturn(flowOf(
            Result.success(mapOf((collectionId to publicKey) to RingPosition.Included(ringIndex, 0, 31)))
        ))
        whenever(membersRepository.subscribeRingStatuses(any(), any())).thenReturn(statuses)
        whenever(membersRepository.getRingKeysPageSize(any())).thenReturn(Result.success(255))
        whenever(timeProvider.now()).thenAnswer { now }
        whenever(voucherRepository.updateLocations(any())).thenAnswer {
            updates += it.getArgument<Map<BandersnatchPublicKey, RecyclerVoucher.Location.InRecycler>>(0)
            Unit
        }
        val service = VoucherLocationService(chainAssetProvider, voucherRepository, membersRepository, instanceIdProvider, timeProvider)

        with(ComputationalScope(backgroundScope)) { service.start() }
        runCurrent()
        now = Instant.fromEpochMilliseconds(61_000L)
        statuses.value = Result.success(mapOf(ringKey to RingStatus(total = 100, included = 32)))
        runCurrent()
        val firstLocation = RecyclerVoucher.Location.InRecycler(ringIndex, 32, now)
        vouchers.value = listOf(vouchers.value.single().copy(location = firstLocation))
        runCurrent()
        now = Instant.fromEpochMilliseconds(121_000L)
        vouchers.value = listOf(vouchers.value.single().copy(location = firstLocation.copy(recyclerMembers = 0, enteredAt = null)))
        runCurrent()

        assertEquals(
            listOf(firstLocation, RecyclerVoucher.Location.InRecycler(ringIndex, 32, now)).map { mapOf(publicKey to it) },
            updates.filter { it.isNotEmpty() }.distinct()
        )
    }
}
