package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.deriveKeypair
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetKind
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetLedger
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.EntryAssets
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.LedgerAsset
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders.ClaimExtrinsicBuilder
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.COINAGE_DOMAIN
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.EnrichedSendableExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.OperationGroupId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPreparation
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Building a claim again, once the engine has proven an attempt of it can never land.
 *
 * These are the claim cases whose deciding moved from the claim loop into the engine: the loop now submits a
 * coin once, and everything about trying again — waiting for the peer's coin, the window, giving up — is
 * this policy's. "Claimed" here means the policy hands back an extrinsic; "not claimed" means it leaves the
 * claim waiting; "ends" means it gives up for good.
 */
@OptIn(ExperimentalTime::class)
class CoinageClaimSubmissionPolicyTest {
    @Before
    fun mockDerivation() = mockkStatic(DERIVATION_FILE)

    @After
    fun unmockDerivation() = unmockkStatic(DERIVATION_FILE)

    private val chain: Chain = mockk()
    private val chainAssetProvider: ChainAssetProvider = mockk()
    private val assetLedger: CoinageAssetLedger = mockk()
    private val coinRepository: CoinRepository = mockk()
    private val claimExtrinsicBuilder: ClaimExtrinsicBuilder = mockk()
    private val timeProvider: TimeProvider = mockk()

    private val policy = CoinageClaimSubmissionPolicy(
        chainAssetProvider = chainAssetProvider,
        assetLedger = assetLedger,
        coinRepository = coinRepository,
        claimExtrinsicBuilder = claimExtrinsicBuilder,
        timeProvider = timeProvider,
    )

    private val builtFor = mutableMapOf<AccountId, EnrichedSendableExtrinsic>()

    @Before
    fun openTheWindow() {
        every { chainAssetProvider.chainId() } returns "test-chain"
        coEvery { chainAssetProvider.chain() } returns chain
        every { timeProvider.now() } returns WINDOW_OPEN
        coEvery { claimExtrinsicBuilder.build(any(), any(), any()) } answers {
            val extrinsic: EnrichedSendableExtrinsic = mockk()
            builtFor[thirdArg()] = extrinsic
            Result.success(extrinsic)
        }
    }

    // ---- when a claim is built ----

    /**
     * The engine proved the claim's last attempt can never land, and the peer's coin is still on chain — so it
     * is still the peer's money sitting there. The claim is built again.
     */
    @Test
    fun `a claim that failed is submitted again while its coin is still on chain`() = runTest {
        val claim = claimOf(1)
        givenLedgerRecords(claim)
        givenChain(look(claim.source))

        val outcome = prepare(claim)

        assertReady(outcome, claim)
    }

    /** Nothing on chain yet: the coin may still be landing, so the claim is not built and not given up. */
    @Test
    fun `a coin that is not on chain yet is not claimed`() = runTest {
        val claim = claimOf(1)
        givenLedgerRecords(claim)
        givenChain(look())

        val outcome = prepare(claim)

        assertStillWaiting(outcome, claim)
        verifyNothingBuilt()
    }

    @Test
    fun `a coin that appears later is claimed when it does`() = runTest {
        val claim = claimOf(1)
        givenLedgerRecords(claim)
        givenChain(look(), after(10.seconds), look(claim.source))

        val outcome = prepare(claim)

        assertReady(outcome, claim)
    }

    /**
     * One coin shows up before the other. Building the first right away would leave the second for a separate
     * call over the same wait, so the call holds out for the whole set while it keeps arriving.
     */
    @Test
    fun `claiming holds out for the whole set before submitting`() = runTest {
        val first = claimOf(1)
        val second = claimOf(2)
        givenLedgerRecords(first, second)
        givenChain(look(first.source), after(10.seconds), look(first.source, second.source))

        val outcome = prepare(first, second)

        assertReady(outcome, first)
        assertReady(outcome, second)
    }

    /** Holding out is bounded: a coin that never arrives must not hold up the one that did. */
    @Test
    fun `a partial detection claims only the coins that arrived`() = runTest {
        val arrived = claimOf(1)
        val missing = claimOf(2)
        givenLedgerRecords(arrived, missing)
        givenChain(look(arrived.source))

        val outcome = prepare(arrived, missing)

        assertReady(outcome, arrived)
        assertStillWaiting(outcome, missing)
    }

    /**
     * The coin was there, then a fork took it away. The newest look wins outright: building against the
     * widest view ever seen would spend a coin the chain no longer has.
     */
    @Test
    fun `a coin a fork took away is not claimed on the strength of an older look`() = runTest {
        val forked = claimOf(1)
        val missing = claimOf(2)
        givenLedgerRecords(forked, missing)
        givenChain(look(forked.source), after(5.seconds), look())

        val outcome = prepare(forked, missing)

        assertStillWaiting(outcome, forked)
        assertStillWaiting(outcome, missing)
        verifyNothingBuilt()
    }

    /** A read that could not be taken says nothing, so it must not replace the look before it. */
    @Test
    fun `a failed read does not erase what the chain last showed`() = runTest {
        val seen = claimOf(1)
        val missing = claimOf(2)
        givenLedgerRecords(seen, missing)
        givenChain(look(seen.source), after(5.seconds), failedRead())

        val outcome = prepare(seen, missing)

        assertReady(outcome, seen)
    }

    // ---- when a claim ends ----

    /** The window closed and a look shows the coin gone: nothing further can make this claim land. */
    @Test
    fun `claiming ends when the window closes on coins that never arrived`() = runTest {
        val claim = claimOf(1)
        givenLedgerRecords(claim)
        givenWindowClosed()
        givenChain(look())

        val outcome = prepare(claim)

        assertGaveUp(outcome, claim)
    }

    /** The window bounds how long we wait for a coin to appear, never whether a coin that is there is taken. */
    @Test
    fun `a coin still on chain is claimed however long ago the payment arrived`() = runTest {
        val claim = claimOf(1)
        givenLedgerRecords(claim)
        givenWindowClosed()
        givenChain(look(claim.source))

        val outcome = prepare(claim)

        assertReady(outcome, claim)
    }

    /**
     * Past the window, one coin is still there and one is gone. The one still there is built again rather than
     * abandoned alongside the one that is gone.
     */
    @Test
    fun `claiming carries on past the window while the coin is still there`() = runTest {
        val stillThere = claimOf(1)
        val gone = claimOf(2)
        givenLedgerRecords(stillThere, gone)
        givenWindowClosed()
        givenChain(look(stillThere.source))

        val outcome = prepare(stillThere, gone)

        assertReady(outcome, stillThere)
        assertGaveUp(outcome, gone)
    }

    /** Inside the window, a coin that never showed up is left waiting: giving up here would strand the money. */
    @Test
    fun `a coin that has not appeared keeps the claim open`() = runTest {
        val claim = claimOf(1)
        givenLedgerRecords(claim)
        givenChain(look(), after(1.seconds), look())

        val outcome = prepare(claim)

        assertStillWaiting(outcome, claim)
    }

    // ---- what a rebuild is ----

    /**
     * A payment we already made out of the claimed coin waits on that exact coin, so a rebuild must mint into
     * the output the first attempt recorded — never into a fresh one.
     */
    @Test
    fun `a rebuilt claim mints to the output already recorded in the ledger`() = runTest {
        val claim = claimOf(1)
        givenLedgerRecords(claim)
        givenChain(look(claim.source))

        prepare(claim)

        coVerify(exactly = 1) { claimExtrinsicBuilder.build(chain, any(), claim.destination) }
    }

    /** The peer's key lives only in the payment message, so the rebuild signs with the one kept in the params. */
    @Test
    fun `a rebuilt claim is signed with the received key from params`() = runTest {
        val claim = claimOf(1)
        givenLedgerRecords(claim)
        givenChain(look(claim.source))

        prepare(claim)

        coVerify(exactly = 1) { claimExtrinsicBuilder.build(any(), claim.keypair, any()) }
    }

    /** A build that failed is the executor's to try again later; calling it a give-up would lose the coin. */
    @Test
    fun `a build failure is reported as a failure, not a give-up`() = runTest {
        val claim = claimOf(1)
        givenLedgerRecords(claim)
        givenChain(look(claim.source))
        coEvery { claimExtrinsicBuilder.build(any(), any(), any()) } returns Result.failure(IllegalStateException("no runtime"))

        val outcome = policy.prepareSubmission(listOf(claim.scheduled))

        assertTrue("expected a failure but was $outcome", outcome.isFailure)
    }

    // ---- harness ----

    private suspend fun prepare(vararg claims: PeerClaim): Map<DurableTxId, SubmissionPreparation> =
        policy.prepareSubmission(claims.map { it.scheduled }).getOrThrow()

    private fun assertReady(outcome: Map<DurableTxId, SubmissionPreparation>, claim: PeerClaim) {
        val ready = outcome[claim.id]
        assertTrue("expected ${claim.id} to be built but was $ready", ready is SubmissionPreparation.Ready)
        assertEquals(builtFor[claim.destination], (ready as SubmissionPreparation.Ready).extrinsic)
    }

    private fun assertStillWaiting(outcome: Map<DurableTxId, SubmissionPreparation>, claim: PeerClaim) {
        assertTrue("expected ${claim.id} to keep waiting but was ${outcome[claim.id]}", claim.id !in outcome)
    }

    private fun assertGaveUp(outcome: Map<DurableTxId, SubmissionPreparation>, claim: PeerClaim) {
        assertEquals(SubmissionPreparation.GiveUp, outcome[claim.id])
    }

    private fun verifyNothingBuilt() {
        coVerify(exactly = 0) { claimExtrinsicBuilder.build(any(), any(), any()) }
    }

    private fun givenWindowClosed() {
        every { timeProvider.now() } returns WINDOW_CLOSED
    }

    private class PeerClaim(
        val scheduled: ScheduledDurableTx,
        val keypair: Sr25519Keypair,
        val source: AccountId,
        val destination: AccountId,
        val outputIndex: Int,
    ) {
        val id: DurableTxId get() = scheduled.id
    }

    /** Derivation is a top-level extension over the SDK's sr25519 factory; only the seam matters here. */
    private fun claimOf(seed: Int): PeerClaim {
        val privateKey: CoinPrivateKey = byteArrayOf(seed.toByte()).toDataByteArray()
        val keypair: Sr25519Keypair = mockk()

        every { keypair.publicKey } returns privateKey.value
        every { privateKey.deriveKeypair() } returns keypair

        val scheduled = ScheduledDurableTx(
            id = DurableTxId(seed.toLong()),
            domainId = COINAGE_DOMAIN,
            groupId = GROUP,
            policy = CoinageSubmissionParams.claimPolicy(ClaimRetryParams(RETRY_UNTIL, privateKey)),
        )

        return PeerClaim(
            scheduled = scheduled,
            keypair = keypair,
            source = privateKey.value.toDataByteArray(),
            destination = byteArrayOf(100, seed.toByte()).toDataByteArray(),
            outputIndex = 100 + seed,
        )
    }

    private fun givenLedgerRecords(vararg claims: PeerClaim) {
        coEvery { assetLedger.assetsOf(any()) } returns Result.success(
            claims.associate { claim ->
                claim.id to EntryAssets(
                    inputs = listOf(LedgerAsset(CoinageAssetKind.COIN, asset = null, publicKey = claim.source)),
                    outputs = listOf(
                        LedgerAsset(CoinageAssetKind.COIN, OwnAsset.Coin(testKey(claim.outputIndex)), claim.destination)
                    ),
                )
            }
        )
    }

    private sealed interface ChainStep

    private class Look(val read: Result<List<AccountId>>) : ChainStep

    private class Pause(val duration: Duration) : ChainStep

    private fun look(vararg present: AccountId): ChainStep = Look(Result.success(present.toList()))

    private fun failedRead(): ChainStep = Look(Result.failure(IllegalStateException("node unreachable")))

    private fun after(duration: Duration): ChainStep = Pause(duration)

    /**
     * What the chain holds, one emission per look. A storage subscription stays open, so the flow does not end
     * after the last emission — which is what lets a claim keep waiting for a coin to appear.
     */
    private fun givenChain(vararg steps: ChainStep) {
        coEvery { coinRepository.subscribeCoinsInfoFor(any(), any()) } answers {
            val requested = secondArg<List<AccountId>>()

            flow {
                steps.forEach { step ->
                    when (step) {
                        is Pause -> delay(step.duration)
                        is Look -> emit(
                            step.read.map { present ->
                                requested.associateWith { accountId ->
                                    OnChainCoinInfo(instanceId = 0, value = 3, age = 0).takeIf { accountId in present }
                                }
                            }
                        )
                    }
                }
                awaitCancellation()
            }
        }
    }

    private companion object {
        val GROUP = OperationGroupId("chat-claim:1")

        val RETRY_UNTIL = Instant.fromEpochSeconds(1_000)
        val WINDOW_OPEN = Instant.fromEpochSeconds(500)
        val WINDOW_CLOSED = Instant.fromEpochSeconds(1_500)

        const val DERIVATION_FILE = "io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferMemoKt"
    }
}
