package io.paritytech.polkadotapp.feature_connection_status_impl.data

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.requests.SharedRequestsBuilder
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.chains.network.binding.BlockNumber
import io.paritytech.polkadotapp.chains.network.binding.toBlockNumber
import io.paritytech.polkadotapp.chains.network.rpc.RpcCalls
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSources
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.math.BigInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BlockProductionAnchorDataSourceTest {
    @Test
    fun `both hashes come off one resolved height`() = runBlocking<Unit> {
        withChainAt(head = 1_000, headTimeMillis = 130_000, previousTimeMillis = 100_000)

        val anchor = dataSource.fetch(CHAIN_ID, BLOCKS_BACK).getOrThrow()

        assertEquals(1_000, anchor.headHeight)
        assertEquals(30.seconds, anchor.chainTimeSpan)
        // Resolved once and both hashes derived from it: reading the best hash and the best height
        // separately would leave them a block apart and flatter the span.
        assertEquals(1, chainState.headReads)
        verify(rpcCalls).getBlockHash(CHAIN_ID, 1_000.toBlockNumber())
        verify(rpcCalls).getBlockHash(CHAIN_ID, (1_000 - BLOCKS_BACK).toBlockNumber())
    }

    @Test
    fun `a chain shorter than the window is rejected before any hash is read`() = runBlocking<Unit> {
        withChainAt(head = BLOCKS_BACK - 1, headTimeMillis = 130_000, previousTimeMillis = 100_000)

        assertTrue(dataSource.fetch(CHAIN_ID, BLOCKS_BACK).isFailure)
    }

    @Test
    fun `timestamps that do not move forward are rejected`() = runBlocking<Unit> {
        withChainAt(head = 1_000, headTimeMillis = 100_000, previousTimeMillis = 100_000)

        assertTrue(dataSource.fetch(CHAIN_ID, BLOCKS_BACK).isFailure)
    }

    private val rpcCalls: RpcCalls = mock()
    private val remoteStorage: StorageDataSource = mock()
    private val chainState = FakeChainStateRepository()

    private val dataSource = BlockProductionAnchorDataSource(
        rpcCalls = rpcCalls,
        storageDataSources = StorageDataSources(remote = remoteStorage, local = mock()),
        chainStateRepository = chainState,
    )

    private suspend fun withChainAt(head: Int, headTimeMillis: Long, previousTimeMillis: Long) {
        chainState.head = head.toBlockNumber()
        // BlockNumber wraps a reference type, so it reaches the mock unboxed as its BigInteger.
        whenever(rpcCalls.getBlockHash(eq(CHAIN_ID), any<BlockNumber>())).thenAnswer { invocation ->
            if (invocation.getArgument<BigInteger>(1) == head.toBigInteger()) HEAD_HASH else PREVIOUS_HASH
        }
        whenever(remoteStorage.query<BigInteger>(eq(CHAIN_ID), eq(HEAD_HASH), any()))
            .thenReturn(BigInteger.valueOf(headTimeMillis))
        whenever(remoteStorage.query<BigInteger>(eq(CHAIN_ID), eq(PREVIOUS_HASH), any()))
            .thenReturn(BigInteger.valueOf(previousTimeMillis))
    }

    // A fake rather than a mock: currentRemoteBlockNumber returns the BlockNumber value class, and
    // Mockito cannot answer a suspend function across that boxing (code/testing.md § Rules at a glance 2).
    private class FakeChainStateRepository : ChainStateRepository {
        var head: BlockNumber = BlockNumber.ZERO
        var headReads: Int = 0

        override suspend fun currentRemoteBlockNumber(chainId: ChainId): BlockNumber {
            headReads++
            return head
        }

        override suspend fun sampledBlockTimeInMillis(chainId: ChainId): BigInteger = unused()
        override suspend fun expectedBlockTime(chainId: ChainId): Duration = unused()
        override suspend fun currentBlock(chainId: ChainId): BlockNumber = unused()
        override suspend fun currentBlockHash(chainId: ChainId): BlockHash = unused()
        override fun currentBlockNumberFlow(chainId: ChainId): Flow<BlockNumber> = unused()
        override suspend fun blockHashCount(chainId: ChainId): BigInteger = unused()
        override suspend fun currentRemoteBlockNumberFlow(
            chainId: ChainId,
            sharedRequestsBuilder: SharedRequestsBuilder?,
        ): Flow<BlockNumber> = unused()

        override suspend fun getFinalizedBlockHash(chainId: ChainId): BlockHash = unused()

        private fun unused(): Nothing = throw UnsupportedOperationException("not used by the anchor")
    }

    private companion object {
        const val CHAIN_ID = "people"
        const val BLOCKS_BACK = 15
        const val HEAD_HASH = "0xhead"
        const val PREVIOUS_HASH = "0xprev"
    }
}
