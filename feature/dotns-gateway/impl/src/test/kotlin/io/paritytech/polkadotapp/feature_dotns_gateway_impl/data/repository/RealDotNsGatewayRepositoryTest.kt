@file:OptIn(ExperimentalStdlibApi::class)

package io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.repository

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTld
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsTldProvider
import io.paritytech.polkadotapp.feature_dotns_gateway_api.domain.model.DotNsBaseNameAvailability
import io.paritytech.polkadotapp.feature_dotns_gateway_api.domain.model.DotNsLink
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.config.DotNsGatewayConfig
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.config.DotNsGatewayConfigProvider
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.contract.PopContractCoder
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.queue.DotNsReservationQueueClearer
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.queue.DotNsReservationQueueReader
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.signer.origins.DotNsGatewayOrigins
import io.paritytech.polkadotapp.feature_revive_api.ReviveContractApi
import io.paritytech.polkadotapp.feature_revive_api.toEvmAccountId
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint64
import java.math.BigInteger

class RealDotNsGatewayRepositoryTest {
    private val knownChains = KnownChains(people = "", assetHub = CHAIN_ID, bulletIn = "", hydration = null)
    private val localStorageSource: StorageDataSource = mock()
    private val remoteStorageSource: StorageDataSource = mock()
    private val reviveContractApi: ReviveContractApi = mock()
    private val configProvider: DotNsGatewayConfigProvider = mock()
    private val tldProvider: DotNsTldProvider = mock()
    private val extrinsicService: ExtrinsicService = mock()
    private val dotNsGatewayOrigins: DotNsGatewayOrigins = mock()
    private val chainRegistry: ChainRegistry = mock()

    private val queueClearer: DotNsReservationQueueClearer = mock()

    private val repository = RealDotNsGatewayRepository(
        localStorageSource = localStorageSource,
        reviveContractApi = reviveContractApi,
        queueReader = DotNsReservationQueueReader(remoteStorageSource, reviveContractApi, knownChains),
        queueClearer = queueClearer,
        configProvider = configProvider,
        tldProvider = tldProvider,
        extrinsicService = extrinsicService,
        dotNsGatewayOrigins = dotNsGatewayOrigins,
        chainRegistry = chainRegistry,
        knownChains = knownChains
    )

    @Test
    fun `TakenByOther when the name is already registered`() = runBlocking<Unit> {
        stubConfig()
        stubChatKey(ByteArray(65) { 7 })

        assertEquals(DotNsBaseNameAvailability.TakenByOther, repository.getBaseNameAvailability(NAME, OUR_ACCOUNT_ID).getOrThrow())
    }

    @Test
    fun `Free when nobody has reserved the name`() = runBlocking<Unit> {
        stubConfig()
        stubChatKey(ByteArray(0))
        withQueue()

        assertAvailability(DotNsBaseNameAvailability.Free)
    }

    @Test
    fun `Free when every queued reservation has expired or been left`() = runBlocking<Unit> {
        stubConfig()
        stubChatKey(ByteArray(0))
        withQueue(expired(OTHER_HOLDER), left(), expired(OUR_HOLDER))

        assertAvailability(DotNsBaseNameAvailability.Free)
    }

    @Test
    fun `ReservedByUs when our reservation heads the queue`() = runBlocking<Unit> {
        stubConfig()
        stubChatKey(ByteArray(0))
        withQueue(active(OUR_HOLDER), active(OTHER_HOLDER))

        assertAvailability(DotNsBaseNameAvailability.ReservedByUs)
    }

    @Test
    fun `ReservedByUs when our active reservation sits behind an expired front`() = runBlocking<Unit> {
        stubConfig()
        stubChatKey(ByteArray(0))
        withQueue(expired(OTHER_HOLDER), left(), active(OUR_HOLDER))

        assertAvailability(DotNsBaseNameAvailability.ReservedByUs)
    }

    @Test
    fun `TakenByOther when someone else holds the live reservation`() = runBlocking<Unit> {
        stubConfig()
        stubChatKey(ByteArray(0))
        withQueue(active(OTHER_HOLDER), active(OUR_HOLDER))

        assertAvailability(DotNsBaseNameAvailability.TakenByOther)
    }

    @Test
    fun `TakenByOther when someone else's active reservation sits behind an expired front`() = runBlocking<Unit> {
        stubConfig()
        stubChatKey(ByteArray(0))
        withQueue(expired(OUR_HOLDER), active(OTHER_HOLDER))

        assertAvailability(DotNsBaseNameAvailability.TakenByOther)
    }

    @Test
    fun `a reservation is active through its last second`() = runBlocking<Unit> {
        stubConfig()
        stubChatKey(ByteArray(0))
        withQueue(QueuedEntry(OTHER_HOLDER, joinedAt = NOW - DURATION))

        assertAvailability(DotNsBaseNameAvailability.TakenByOther)
    }

    @Test
    fun `registerName does not submit the claim when the queue cannot be cleared`() = runBlocking<Unit> {
        stubConfig()
        withClaimPreparationFailing()

        val result = repository.registerName(OUR_ACCOUNT_ID, NAME, DotNsLink.None(ByteArray(65).toDataByteArray()))

        assertTrue("expected the preparation failure but was ${result.exceptionOrNull()}", result.exceptionOrNull() === PREPARATION_FAILURE)
        verify(extrinsicService, never()).submitExtrinsicAndAwaitExecution(any(), any(), any(), any(), any())
    }

    private fun withClaimPreparationFailing() = runBlocking {
        val chain: Chain = mock()
        whenever(chainRegistry.getChain(CHAIN_ID)).thenReturn(chain)
        whenever(queueClearer.prepareClaim(eq(chain), eq(OUR_ACCOUNT_ID), eq(NAME), any())).thenReturn(Result.failure(PREPARATION_FAILURE))
    }

    private suspend fun assertAvailability(expected: DotNsBaseNameAvailability) {
        val result = repository.getBaseNameAvailability(NAME, OUR_ACCOUNT_ID)
        assertEquals("unexpected result ${result.exceptionOrNull()}", Result.success(expected), result)
    }

    private fun stubConfig() = runBlocking {
        whenever(configProvider.getConfig()).thenReturn(
            Result.success(DotNsGatewayConfig(popControllerAddress = CONTROLLER, popResolverAddress = RESOLVER))
        )
        whenever(tldProvider.getTld()).thenReturn(Result.success(requireNotNull(DotNsTld.parse("dot"))))
    }

    private fun stubChatKey(key: ByteArray) = runBlocking {
        val output = abiEncodeReturnValues(
            listOf(DynamicBytes(key)),
            listOf(object : TypeReference<DynamicBytes>() {})
        )
        whenever(reviveContractApi.callReadOnly(eq(CHAIN_ID), eq(RESOLVER), any(), isNull()))
            .thenReturn(Result.success(output.toDataByteArray()))
    }

    private class QueuedEntry(val owner: ByteArray, val joinedAt: Long)

    private fun active(owner: ByteArray) = QueuedEntry(owner, joinedAt = NOW - 10)

    private fun expired(owner: ByteArray) = QueuedEntry(owner, joinedAt = NOW - DURATION - 1)

    private fun left() = QueuedEntry(ByteArray(20), joinedAt = NOW - 10)

    private fun withQueue(vararg entries: QueuedEntry) = runBlocking {
        val labelHash = PopContractCoder.reservationLabelHash(NAME)
        stubController(
            PopContractCoder.encodeReservationMeta(labelHash),
            abiEncodeReturnValues(
                listOf(Uint64(HEAD), Uint64(HEAD + entries.size)),
                listOf(object : TypeReference<Uint64>() {}, object : TypeReference<Uint64>() {})
            )
        )
        stubController(
            PopContractCoder.encodeReservationDuration(),
            abiEncodeReturnValues(listOf(Uint64(DURATION)), listOf(object : TypeReference<Uint64>() {}))
        )
        entries.forEachIndexed { offset, entry ->
            stubController(
                PopContractCoder.encodeReservationEntry(labelHash, BigInteger.valueOf(HEAD + offset)),
                abiEncodeReturnValues(
                    listOf(Address("0x" + entry.owner.toHexString()), Uint64(entry.joinedAt)),
                    listOf(object : TypeReference<Address>() {}, object : TypeReference<Uint64>() {})
                )
            )
        }
        whenever(remoteStorageSource.query<BigInteger>(eq(CHAIN_ID), isNull(), any()))
            .thenReturn(BigInteger.valueOf(NOW * 1000))
    }

    private fun stubController(input: ByteArray, output: ByteArray) = runBlocking {
        whenever(reviveContractApi.callReadOnly(eq(CHAIN_ID), eq(CONTROLLER), eq(input.toDataByteArray()), isNull()))
            .thenReturn(Result.success(output.toDataByteArray()))
    }

    @Suppress("UNCHECKED_CAST")
    private fun abiEncodeReturnValues(values: List<Type<*>>, typeRefs: List<TypeReference<out Type<*>>>): ByteArray {
        val function = Function("_", values, typeRefs as List<TypeReference<Type<*>>>)
        val encoded = FunctionEncoder.encode(function).fromHex()
        return encoded.copyOfRange(4, encoded.size)
    }

    private companion object {
        const val CHAIN_ID = "asset-hub-chain-id"
        const val NAME = "byteboro"
        val OUR_ACCOUNT_ID: AccountId = ByteArray(32) { 1 }.toDataByteArray()
        val CONTROLLER: DataByteArray = ByteArray(20) { 4 }.toDataByteArray()
        val RESOLVER: DataByteArray = ByteArray(20) { 5 }.toDataByteArray()
        val OUR_HOLDER: ByteArray = OUR_ACCOUNT_ID.toEvmAccountId().value
        val OTHER_HOLDER: ByteArray = ByteArray(20) { 9 }
        const val HEAD = 7L
        const val DURATION = 1_000L
        const val NOW = 10_000L
        val PREPARATION_FAILURE = IllegalStateException("queue cannot be cleared")
    }
}
