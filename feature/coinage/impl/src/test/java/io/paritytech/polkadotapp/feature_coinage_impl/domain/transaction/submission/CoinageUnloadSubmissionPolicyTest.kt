package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchAlias
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchEntropy
import io.paritytech.polkadotapp.bandersnatch_crypto.aliasInContext
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinProvenance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageKeyIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.CoinageInstanceIdProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.VoucherRingDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.FreeUnloadTokenResolver
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.UnloadTokenResolverFactory
import io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.createForCollection
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.RecyclerProofDataProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.context.CoinageSigningContextProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.signer.origins.CoinageTransactionOrigins
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetKind
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetLedger
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.EntryAssets
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.LedgerAsset
import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.toSplitDestinations
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders.UnloadExtrinsicBuilder
import io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling.UnloadQuotaTracker
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.COINAGE_DOMAIN
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.feature_members_api.data.model.RingRevision
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleMembershipProver
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.EnrichedSendableExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.OperationGroupId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPreparation
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionOrigin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Building a payment's recycler unloads in the background: for the first time once the payment is saved, and
 * again once an attempt is proven unable to land.
 *
 * An unload's inputs are vouchers, and a voucher counts as present while it sits in a recycler — that is where
 * an unload proves it. The ring-VRF proofs make building the slow part, so everything that can be built in one
 * call is built together: one pinned block, one person proof, one token resolution.
 *
 * The builder is real, over mocked collaborators: its `build` takes a context parameter and returns a
 * `Result`, which mockk cannot stub, and building together is exactly what these tests are about.
 */
@OptIn(ExperimentalTime::class)
class CoinageUnloadSubmissionPolicyTest {
    private val chain: Chain = mockk()
    private val peopleCollection: PeopleCollection = mockk()
    private val chainAssetProvider: ChainAssetProvider = mockk()
    private val assetLedger: CoinageAssetLedger = mockk()
    private val coinRepository: CoinRepository = mockk()
    private val voucherRepository: VoucherRepository = mockk()
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase = mockk()
    private val timeProvider: TimeProvider = mockk()

    private val chainStateRepository: ChainStateRepository = mockk()
    private val originFactory: CoinageTransactionOrigins = mockk()
    private val signingContextProvider: CoinageSigningContextProvider = mockk()
    private val voucherRingDerivation: VoucherRingDerivation = mockk()
    private val recyclerProofDataProvider: RecyclerProofDataProvider = mockk()
    private val tokenResolverFactory: UnloadTokenResolverFactory = mockk()
    private val tokenResolver: FreeUnloadTokenResolver = mockk()
    private val extrinsicService: ExtrinsicService = mockk()
    private val peopleMembershipProver: PeopleMembershipProver = mockk()
    private val quotaTracker: UnloadQuotaTracker = mockk()
    private val instanceIdProvider: CoinageInstanceIdProvider = mockk()

    private val unloadExtrinsicBuilder = UnloadExtrinsicBuilder(
        chainStateRepository = chainStateRepository,
        originFactory = originFactory,
        coinageSigningContextProvider = signingContextProvider,
        voucherRingDerivation = voucherRingDerivation,
        recyclerProofDataProvider = recyclerProofDataProvider,
        unloadTokenResolverFactory = tokenResolverFactory,
        extrinsicService = extrinsicService,
        peopleMembershipProver = peopleMembershipProver,
        quotaTracker = quotaTracker,
        coinageInstanceIdProvider = instanceIdProvider,
    )

    private val policy = CoinageUnloadSubmissionPolicy(
        chainAssetProvider = chainAssetProvider,
        assetLedger = assetLedger,
        coinRepository = coinRepository,
        voucherRepository = voucherRepository,
        activePeopleCollectionUseCase = activePeopleCollectionUseCase,
        unloadExtrinsicBuilder = unloadExtrinsicBuilder,
        timeProvider = timeProvider,
    )

    private val knownCoins = mutableListOf<Coin>()
    private val knownVouchers = mutableListOf<RecyclerVoucher>()

    /** Every build call, as the voucher sets of the unloads it built, in the order they were built. */
    private val buildCalls = mutableListOf<MutableList<List<CoinageKeyIndex>>>()
    private val builtFor = mutableMapOf<CoinageKeyIndex, EnrichedSendableExtrinsic>()
    private val tokensUsed = mutableListOf<FreeUnloadTokenResolver.ResolvedUnloadToken>()
    private val destinationsBuilt = mutableListOf<List<Coin>>()
    private val originVouchers = mutableMapOf<TransactionOrigin, List<CoinageKeyIndex>>()

    @Before
    fun mockExtensions() {
        mockkStatic(TOKEN_RESOLVER_FILE, ALIAS_FILE, DESTINATIONS_FILE)
    }

    @After
    fun unmockExtensions() {
        unmockkStatic(TOKEN_RESOLVER_FILE, ALIAS_FILE, DESTINATIONS_FILE)
    }

    @Before
    fun openTheWindow() {
        every { chainAssetProvider.chainId() } returns "test-chain"
        coEvery { chainAssetProvider.chain() } returns chain
        coEvery { activePeopleCollectionUseCase.getActivePeopleCollection() } returns peopleCollection
        every { timeProvider.now() } returns WINDOW_OPEN
        coEvery { coinRepository.getCoinsBy(any()) } answers {
            val requested = firstArg<List<CoinageKeyIndex>>()
            knownCoins.filter { it.derivationIndex in requested }
        }
        coEvery { voucherRepository.getByRingVrfKeyIndices(any()) } answers {
            val requested = firstArg<List<CoinageKeyIndex>>()
            knownVouchers.filter { it.ringVrfKeyIndex in requested }
        }
        givenBuilderCollaborators()
        givenBuildsSucceed()
    }

    // ---- when a transfer is built ----

    @Test
    fun `a scheduled transfer is built once its inputs are on chain`() = runTest {
        val unload = unloadOf(1)
        givenLedgerRecords(unload)
        givenRecyclers(holding(unload))

        val outcome = prepare(unload)

        assertBuilt(outcome, unload)
    }

    /** A voucher not yet in a recycler may still be landing there, so its absence is no reason to give up. */
    @Test
    fun `a transfer whose input is not on chain yet is not built`() = runTest {
        val unload = unloadOf(1)
        givenLedgerRecords(unload)
        givenRecyclers(holding())

        val outcome = prepare(unload)

        assertLeftWaiting(outcome, unload)
        verifyNothingBuilt()
    }

    @Test
    fun `an input that appears later is built when it does`() = runTest {
        val unload = unloadOf(1)
        givenLedgerRecords(unload)
        givenRecyclers(holding(), after(10.seconds), holding(unload))

        val outcome = prepare(unload)

        assertBuilt(outcome, unload)
    }

    @Test
    fun `a bucket holds out for every entry's inputs before building`() = runTest {
        val first = unloadOf(1)
        val second = unloadOf(2)
        givenLedgerRecords(first, second)
        givenRecyclers(holding(first), after(10.seconds), holding(first, second))

        val outcome = prepare(first, second)

        assertBuilt(outcome, first)
        assertBuilt(outcome, second)
        assertEquals("expected the whole bucket in one build", 1, buildCalls.size)
    }

    @Test
    fun `a partial detection builds only the entries whose inputs arrived`() = runTest {
        val arrived = unloadOf(1)
        val missing = unloadOf(2)
        givenLedgerRecords(arrived, missing)
        givenRecyclers(holding(arrived))

        val outcome = prepare(arrived, missing)

        assertBuilt(outcome, arrived)
        assertLeftWaiting(outcome, missing)
    }

    /** A voucher that left its recycler since the last look is not unloaded on the strength of that look. */
    @Test
    fun `an input a fork took away is not built on the strength of an older look`() = runTest {
        val forked = unloadOf(1)
        val missing = unloadOf(2)
        givenLedgerRecords(forked, missing)
        givenRecyclers(holding(forked), after(5.seconds), holding())

        val outcome = prepare(forked, missing)

        assertLeftWaiting(outcome, forked)
        verifyNothingBuilt()
    }

    // ---- when a transfer ends ----

    @Test
    fun `a transfer gives up when the window closes on inputs that never arrived`() = runTest {
        val unload = unloadOf(1)
        givenLedgerRecords(unload)
        givenWindowClosed()
        givenRecyclers(holding())

        val outcome = prepare(unload)

        assertGaveUp(outcome, unload)
    }

    @Test
    fun `an input still on chain is built however long ago the payment was sent`() = runTest {
        val unload = unloadOf(1)
        givenLedgerRecords(unload)
        givenWindowClosed()
        givenRecyclers(holding(unload))

        val outcome = prepare(unload)

        assertBuilt(outcome, unload)
    }

    @Test
    fun `building carries on past the window while the input is still there`() = runTest {
        val stillThere = unloadOf(1)
        val gone = unloadOf(2)
        givenLedgerRecords(stillThere, gone)
        givenWindowClosed()
        givenRecyclers(holding(stillThere))

        val outcome = prepare(stillThere, gone)

        assertBuilt(outcome, stillThere)
        assertGaveUp(outcome, gone)
    }

    @Test
    fun `a first build is made even when the window has already closed`() = runTest {
        val neverBuilt = unloadOf(1)
        givenLedgerRecords(neverBuilt)
        givenWindowClosed()
        givenRecyclers(holding(neverBuilt))

        val outcome = prepare(neverBuilt)

        assertBuilt(outcome, neverBuilt)
    }

    @Test
    fun `an input that has not appeared keeps the transfer open`() = runTest {
        val unload = unloadOf(1)
        givenLedgerRecords(unload)
        givenRecyclers(holding(), after(1.seconds), holding())

        val outcome = prepare(unload)

        assertLeftWaiting(outcome, unload)
    }

    // ---- what a rebuild is ----

    /** The recipient holds the keys of exactly these coins, and destinations are positional. */
    @Test
    fun `a rebuild mints to exactly the outputs recorded in the ledger, in order`() = runTest {
        val unload = unloadOf(1)
        givenLedgerRecords(unload)
        givenRecyclers(holding(unload))
        knownCoins.reverse()
        knownVouchers.reverse()

        prepare(unload)

        assertEquals(listOf(unload.outputCoins), destinationsBuilt)
        assertEquals(listOf(listOf(unload.voucherIndices)), buildCalls)
    }

    @Test
    fun `a build failure is reported as a failure, not a give-up`() = runTest {
        val unload = unloadOf(1)
        givenLedgerRecords(unload)
        givenRecyclers(holding(unload))
        givenBuildFails()

        val outcome = policy.prepareSubmission(listOf(unload.scheduled))

        assertTrue("expected a failure but was $outcome", outcome.isFailure)
    }

    // ---- building together ----

    /**
     * The pinned block, the person proof and the token resolution are paid once per call, so every unload
     * ready in one call is built on them rather than each paying for its own.
     */
    @Test
    fun `one pinned block, one prover and one token resolution per call`() = runTest {
        val first = unloadOf(1)
        val second = unloadOf(2)
        givenLedgerRecords(first, second)
        givenRecyclers(holding(first, second))

        prepare(first, second)

        coVerify(exactly = 1) { chainStateRepository.currentBlockHash(any()) }
        coVerify(exactly = 1) { peopleMembershipProver.precomputeForMember(any(), any(), any()) }
        coVerify(exactly = 1) { tokenResolver.resolve(any(), 2) }
    }

    /** Two unloads spending one free token would let only one of them land, so each gets its own. */
    @Test
    fun `every entry in a call gets a distinct free unload token`() = runTest {
        val first = unloadOf(1)
        val second = unloadOf(2)
        givenLedgerRecords(first, second)
        givenRecyclers(holding(first, second))

        prepare(first, second)

        assertEquals(2, tokensUsed.size)
        assertEquals(2, tokensUsed.map { it.counter }.distinct().size)
    }

    /** A token picked for a transaction that was never built is still there to be picked again. */
    @Test
    fun `the unload quota is noted only after a build succeeds`() = runTest {
        val unload = unloadOf(1)
        givenLedgerRecords(unload)
        givenRecyclers(holding(unload))
        givenBuildFails()

        policy.prepareSubmission(listOf(unload.scheduled))

        coVerify(exactly = 0) { quotaTracker.noteUnloadsHappened(any()) }

        givenBuildsSucceed()
        prepare(unload)

        coVerify(exactly = 1) { quotaTracker.noteUnloadsHappened(1) }
    }

    // ---- whether a failure is retried ----

    @Test
    fun `a transfer scheduled without a retry window is never retried`() = runTest {
        val unload = unloadOf(1, retryUntil = null)

        assertFalse(policy.canRetry(mockk(), unload.scheduled.policy.params))
    }

    @Test
    fun `a transfer scheduled with a retry window is retried`() = runTest {
        val unload = unloadOf(1)

        assertTrue(policy.canRetry(mockk(), unload.scheduled.policy.params))
    }

    // ---- harness ----

    private suspend fun prepare(vararg unloads: ScheduledUnload): Map<DurableTxId, SubmissionPreparation> =
        policy.prepareSubmission(unloads.map { it.scheduled }).getOrThrow()

    private fun assertBuilt(outcome: Map<DurableTxId, SubmissionPreparation>, unload: ScheduledUnload) {
        val ready = outcome[unload.id]
        assertTrue("expected ${unload.id} to be built but was $ready", ready is SubmissionPreparation.Ready)
        assertEquals(builtFor[unload.voucherIndices.first()], (ready as SubmissionPreparation.Ready).extrinsic)
    }

    private fun assertLeftWaiting(outcome: Map<DurableTxId, SubmissionPreparation>, unload: ScheduledUnload) {
        assertTrue("expected ${unload.id} to keep waiting but was ${outcome[unload.id]}", unload.id !in outcome)
    }

    private fun assertGaveUp(outcome: Map<DurableTxId, SubmissionPreparation>, unload: ScheduledUnload) {
        assertEquals(SubmissionPreparation.GiveUp, outcome[unload.id])
    }

    private fun verifyNothingBuilt() {
        assertTrue("expected no build but got $buildCalls", buildCalls.isEmpty())
    }

    private fun givenWindowClosed() {
        every { timeProvider.now() } returns WINDOW_CLOSED
    }

    private fun givenBuilderCollaborators() {
        every { chain.id } returns "test-chain"
        coEvery { chainStateRepository.currentBlockHash(any()) } returns "0xpinned"
        coEvery { recyclerProofDataProvider.getRecyclerRevisions(any(), any(), any()) } answers {
            Result.success(secondArg<Collection<RecyclerKey>>().associateWith { RingRevision(1) })
        }
        every { tokenResolverFactory.createForCollection(any()) } returns tokenResolver
        coEvery { tokenResolver.resolve(any(), any()) } answers {
            buildCalls += mutableListOf<List<CoinageKeyIndex>>()
            List(secondArg<Int>()) {
                FreeUnloadTokenResolver.ResolvedUnloadToken(period = 1, counter = it.toLong(), unloadTokenContext = BandersnatchContext(byteArrayOf(9)))
            }
        }
        coEvery { peopleMembershipProver.precomputeForMember(any(), any(), any()) } returns Result.success(mockk())
        coEvery { instanceIdProvider.instanceId() } returns Result.success(1u)
        every { signingContextProvider.recyclerVouchersContext() } returns BandersnatchContext(byteArrayOf(8))
        coEvery { voucherRingDerivation.deriveBandersnatch(any()) } returns BandersnatchEntropy(byteArrayOf(7))
        every { any<BandersnatchEntropy>().aliasInContext(any()) } returns BandersnatchAlias(byteArrayOf(6))
        every { any<List<Coin>>().toSplitDestinations() } answers {
            // A static extension's receiver arrives as its first argument.
            destinationsBuilt += firstArg<List<Coin>>()
            emptyList()
        }
        coEvery { quotaTracker.noteUnloadsHappened(any()) } returns Unit
        every { originFactory.createAsUnloadTokenPeopleOrigin(any(), any(), any(), any(), any()) } answers {
            val origin: TransactionOrigin = mockk()
            originVouchers[origin] = secondArg<List<RecyclerVoucher>>().map { it.ringVrfKeyIndex }
            tokensUsed += thirdArg<FreeUnloadTokenResolver.ResolvedUnloadToken>()
            origin
        }
    }

    private fun givenBuildsSucceed() {
        coEvery { extrinsicService.buildExtrinsic(any(), any(), any(), any()) } answers {
            val vouchers = originVouchers.getValue(secondArg())
            buildCalls.last() += vouchers

            mockk<EnrichedSendableExtrinsic>()
                .also { builtFor[vouchers.first()] = it }
                .let { Result.success(it) }
        }
    }

    private fun givenBuildFails() {
        coEvery { extrinsicService.buildExtrinsic(any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("no ring revision"))
    }

    private class ScheduledUnload(
        val scheduled: ScheduledDurableTx,
        val vouchers: List<RecyclerVoucher>,
        val outputCoins: List<Coin>,
    ) {
        val id: DurableTxId get() = scheduled.id
        val voucherIndices: List<CoinageKeyIndex> get() = vouchers.map { it.ringVrfKeyIndex }
    }

    /** Two vouchers unloaded into two coins; a seed keeps different unloads' assets apart. */
    private fun unloadOf(seed: Int, retryUntil: Instant? = RETRY_UNTIL): ScheduledUnload {
        val vouchers = listOf(voucherOf(seed * 10), voucherOf(seed * 10 + 1))
        val outputs = listOf(coinOf(seed * 10 + 2), coinOf(seed * 10 + 3))
        knownVouchers += vouchers
        knownCoins += outputs

        val scheduled = ScheduledDurableTx(
            id = DurableTxId(seed.toLong()),
            domainId = COINAGE_DOMAIN,
            groupId = GROUP,
            policy = CoinageSubmissionParams.unloadPolicy(TransferSubmissionParams(retryUntil)),
        )

        return ScheduledUnload(scheduled, vouchers, outputs)
    }

    private fun voucherOf(item: Int): RecyclerVoucher = mockk<RecyclerVoucher>().also {
        every { it.ringVrfKeyIndex } returns testKey(item)
        every { it.recyclerValue } returns ValueExponent(3)
        every { it.location } returns RecyclerVoucher.Location.InRecycler(
            recyclerIndex = RingIndex(BigInteger.ZERO),
            recyclerMembers = 10,
            enteredAt = null,
        )
    }

    private fun coinOf(item: Int) = Coin(
        derivationIndex = testKey(item),
        valueExponent = ValueExponent(3),
        age = Coin.Age.Unknown,
        isOnChain = false,
        accountId = byteArrayOf(item.toByte()).toDataByteArray(),
        provenance = CoinProvenance.UNKNOWN,
    )

    private fun givenLedgerRecords(vararg unloads: ScheduledUnload) {
        coEvery { assetLedger.assetsOf(any()) } returns Result.success(
            unloads.associate { unload ->
                unload.id to EntryAssets(
                    inputs = unload.voucherIndices.map { index ->
                        LedgerAsset(CoinageAssetKind.VOUCHER, OwnAsset.Voucher(index), byteArrayOf(index.item.toByte(), 1).toDataByteArray())
                    },
                    outputs = unload.outputCoins.map {
                        LedgerAsset(CoinageAssetKind.COIN, OwnAsset.Coin(it.derivationIndex), it.accountId)
                    },
                )
            }
        )
    }

    private sealed interface RecyclerStep

    private class Holding(val indices: Set<CoinageKeyIndex>) : RecyclerStep

    private class Pause(val duration: Duration) : RecyclerStep

    private fun holding(vararg unloads: ScheduledUnload): RecyclerStep =
        Holding(unloads.flatMapTo(mutableSetOf()) { it.voucherIndices })

    private fun after(duration: Duration): RecyclerStep = Pause(duration)

    /** One emission per change of the recyclers, and a subscription that stays open after the last one. */
    private fun givenRecyclers(vararg steps: RecyclerStep) {
        every { voucherRepository.subscribeVouchersInRecycler() } returns flow {
            steps.forEach { step ->
                when (step) {
                    is Pause -> delay(step.duration)
                    is Holding -> emit(knownVouchers.filter { it.ringVrfKeyIndex in step.indices })
                }
            }
            awaitCancellation()
        }
    }

    private companion object {
        const val TOKEN_RESOLVER_FILE = "io.paritytech.polkadotapp.feature_coinage_impl.data.helpers.FreeUnloadTokenResolverKt"
        const val ALIAS_FILE = "io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchEntropyKt"
        const val DESTINATIONS_FILE = "io.paritytech.polkadotapp.feature_coinage_impl.domain.model.SplitDestinationKt"

        val GROUP = OperationGroupId("chat-send")

        val RETRY_UNTIL = Instant.fromEpochSeconds(1_000)
        val WINDOW_OPEN = Instant.fromEpochSeconds(500)
        val WINDOW_CLOSED = Instant.fromEpochSeconds(1_500)
    }
}
