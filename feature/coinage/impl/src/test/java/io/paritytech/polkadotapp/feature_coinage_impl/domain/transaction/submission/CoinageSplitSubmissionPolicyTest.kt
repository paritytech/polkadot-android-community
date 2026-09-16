package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinProvenance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageKeyIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetKind
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetLedger
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.EntryAssets
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.LedgerAsset
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders.SplitExtrinsicBuilder
import io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.COINAGE_DOMAIN
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.data.EnrichedSendableExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableFailureKind
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.OperationGroupId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPreparation
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Building a payment's split in the background: for the first time once the payment is saved, and again once
 * an attempt is proven unable to land.
 *
 * The recipient already holds the keys of the coins this split mints, so the two ways to get it wrong are the
 * ones that strand them: building against a coin that is not there, or giving up on one that is. "Built" here
 * means the policy hands back an extrinsic; "left waiting" means it does not decide; "gives up" is final.
 */
@OptIn(ExperimentalTime::class)
class CoinageSplitSubmissionPolicyTest {
    private val chain: Chain = mockk()
    private val chainAssetProvider: ChainAssetProvider = mockk()
    private val assetLedger: CoinageAssetLedger = mockk()
    private val coinRepository: CoinRepository = mockk()
    private val splitExtrinsicBuilder: SplitExtrinsicBuilder = mockk()
    private val timeProvider: TimeProvider = mockk()

    private val policy = CoinageSplitSubmissionPolicy(
        chainAssetProvider = chainAssetProvider,
        assetLedger = assetLedger,
        coinRepository = coinRepository,
        splitExtrinsicBuilder = splitExtrinsicBuilder,
        timeProvider = timeProvider,
    )

    private val builtFor = mutableMapOf<AccountId, EnrichedSendableExtrinsic>()
    private val knownCoins = mutableListOf<Coin>()

    @Before
    fun openTheWindow() {
        every { chainAssetProvider.chainId() } returns "test-chain"
        coEvery { chainAssetProvider.chain() } returns chain
        every { timeProvider.now() } returns WINDOW_OPEN
        coEvery { coinRepository.getCoinsBy(any()) } answers {
            val requested = firstArg<List<CoinageKeyIndex>>()
            knownCoins.filter { it.derivationIndex in requested }
        }
        coEvery { splitExtrinsicBuilder.build(any(), any(), any()) } answers {
            val extrinsic: EnrichedSendableExtrinsic = mockk()
            builtFor[secondArg<Coin>().accountId] = extrinsic
            Result.success(extrinsic)
        }
    }

    // ---- when a transfer is built ----

    @Test
    fun `a scheduled transfer is built once its inputs are on chain`() = runTest {
        val split = splitOf(1)
        givenLedgerRecords(split)
        givenChain(look(split.input))

        val outcome = prepare(split)

        assertBuilt(outcome, split)
    }

    /** The coin being split may be the output of a claim still landing, so its absence is no reason to give up. */
    @Test
    fun `a transfer whose input is not on chain yet is not built`() = runTest {
        val split = splitOf(1)
        givenLedgerRecords(split)
        givenChain(look())

        val outcome = prepare(split)

        assertLeftWaiting(outcome, split)
        verifyNothingBuilt()
    }

    @Test
    fun `an input that appears later is built when it does`() = runTest {
        val split = splitOf(1)
        givenLedgerRecords(split)
        givenChain(look(), after(10.seconds), look(split.input))

        val outcome = prepare(split)

        assertBuilt(outcome, split)
    }

    @Test
    fun `a bucket holds out for every entry's inputs before building`() = runTest {
        val first = splitOf(1)
        val second = splitOf(2)
        givenLedgerRecords(first, second)
        givenChain(look(first.input), after(10.seconds), look(first.input, second.input))

        val outcome = prepare(first, second)

        assertBuilt(outcome, first)
        assertBuilt(outcome, second)
    }

    @Test
    fun `a partial detection builds only the entries whose inputs arrived`() = runTest {
        val arrived = splitOf(1)
        val missing = splitOf(2)
        givenLedgerRecords(arrived, missing)
        givenChain(look(arrived.input))

        val outcome = prepare(arrived, missing)

        assertBuilt(outcome, arrived)
        assertLeftWaiting(outcome, missing)
    }

    /** The newest look wins outright: building against the widest view ever seen would spend a coin that is gone. */
    @Test
    fun `an input a fork took away is not built on the strength of an older look`() = runTest {
        val forked = splitOf(1)
        val missing = splitOf(2)
        givenLedgerRecords(forked, missing)
        givenChain(look(forked.input), after(5.seconds), look())

        val outcome = prepare(forked, missing)

        assertLeftWaiting(outcome, forked)
        verifyNothingBuilt()
    }

    @Test
    fun `a failed read does not erase what the chain last showed`() = runTest {
        val seen = splitOf(1)
        val missing = splitOf(2)
        givenLedgerRecords(seen, missing)
        givenChain(look(seen.input), after(5.seconds), failedRead())

        val outcome = prepare(seen, missing)

        assertBuilt(outcome, seen)
    }

    // ---- when a transfer ends ----

    @Test
    fun `a transfer gives up when the window closes on inputs that never arrived`() = runTest {
        val split = splitOf(1)
        givenLedgerRecords(split)
        givenWindowClosed()
        givenChain(look())

        val outcome = prepare(split)

        assertGaveUp(outcome, split)
    }

    /** The window bounds how long an absent input is waited for, never whether a present one is used. */
    @Test
    fun `an input still on chain is built however long ago the payment was sent`() = runTest {
        val split = splitOf(1)
        givenLedgerRecords(split)
        givenWindowClosed()
        givenChain(look(split.input))

        val outcome = prepare(split)

        assertBuilt(outcome, split)
    }

    @Test
    fun `building carries on past the window while the input is still there`() = runTest {
        val stillThere = splitOf(1)
        val gone = splitOf(2)
        givenLedgerRecords(stillThere, gone)
        givenWindowClosed()
        givenChain(look(stillThere.input))

        val outcome = prepare(stillThere, gone)

        assertBuilt(outcome, stillThere)
        assertGaveUp(outcome, gone)
    }

    /** A payment first seen by the executor after its window closed — say, after a long time offline — is still tried. */
    @Test
    fun `a first build is made even when the window has already closed`() = runTest {
        val neverBuilt = splitOf(1)
        givenLedgerRecords(neverBuilt)
        givenWindowClosed()
        givenChain(look(neverBuilt.input))

        val outcome = prepare(neverBuilt)

        assertBuilt(outcome, neverBuilt)
    }

    @Test
    fun `an input that has not appeared keeps the transfer open`() = runTest {
        val split = splitOf(1)
        givenLedgerRecords(split)
        givenChain(look(), after(1.seconds), look())

        val outcome = prepare(split)

        assertLeftWaiting(outcome, split)
    }

    /** A transfer with no window — the merchant path — is abandoned the moment a look shows its input missing. */
    @Test
    fun `a transfer scheduled without a retry window gives up as soon as its input is seen missing`() = runTest {
        val split = splitOf(1, retryUntil = WINDOW_OPEN, retryFailures = false)
        givenLedgerRecords(split)
        givenChain(look())

        val outcome = prepare(split)

        assertGaveUp(outcome, split)
    }

    // ---- what a rebuild is ----

    /**
     * The recipient holds the keys of exactly these coins, and the split's destinations are positional — so a
     * rebuild mints the recorded outputs in the recorded order, whatever order the coin table returns them in.
     */
    @Test
    fun `a rebuild mints to exactly the outputs recorded in the ledger, in order`() = runTest {
        val split = splitOf(1)
        givenLedgerRecords(split)
        givenChain(look(split.input))
        knownCoins.reverse()

        prepare(split)

        coVerify(exactly = 1) { splitExtrinsicBuilder.build(chain, split.inputCoin, split.outputCoins) }
    }

    @Test
    fun `a build failure is reported as a failure, not a give-up`() = runTest {
        val split = splitOf(1)
        givenLedgerRecords(split)
        givenChain(look(split.input))
        coEvery { splitExtrinsicBuilder.build(any(), any(), any()) } returns Result.failure(IllegalStateException("no runtime"))

        val outcome = policy.prepareSubmission(listOf(split.scheduled))

        assertTrue("expected a failure but was $outcome", outcome.isFailure)
    }

    // ---- whether a failure is retried ----

    @Test
    fun `a transfer scheduled without a retry window is never retried`() = runTest {
        val split = splitOf(1, retryFailures = false)

        assertFalse(policy.canRetry(mockk(), split.scheduled.policy.params, DurableFailureKind.EXPIRED))
    }

    @Test
    fun `a transfer scheduled with a retry window is retried`() = runTest {
        val split = splitOf(1)

        assertTrue(policy.canRetry(mockk(), split.scheduled.policy.params, DurableFailureKind.EXPIRED))
    }

    /** An attempt that simply never got included may land if built again, however late that is. */
    @Test
    fun `an attempt that expired is retried even after the window`() = runTest {
        val split = splitOf(1)
        givenWindowClosed()

        assertTrue(policy.canRetry(mockk(), split.scheduled.policy.params, DurableFailureKind.EXPIRED))
    }

    /** A dispatch failure would most likely repeat, so only the window bounds how often it is rebuilt. */
    @Test
    fun `a dispatch failure is retried only while the window is open`() = runTest {
        val split = splitOf(1)

        assertTrue(policy.canRetry(mockk(), split.scheduled.policy.params, DurableFailureKind.DISPATCH_FAILED))

        givenWindowClosed()

        assertFalse(policy.canRetry(mockk(), split.scheduled.policy.params, DurableFailureKind.DISPATCH_FAILED))
    }

    @Test
    fun `a rejected attempt is retried only while the window is open`() = runTest {
        val split = splitOf(1)

        assertTrue(policy.canRetry(mockk(), split.scheduled.policy.params, DurableFailureKind.REJECTED))

        givenWindowClosed()

        assertFalse(policy.canRetry(mockk(), split.scheduled.policy.params, DurableFailureKind.REJECTED))
    }

    /** The merchant path builds its transfer once: no failure, however it happened, is built again. */
    @Test
    fun `a transfer scheduled to be built once is never retried`() = runTest {
        val split = splitOf(1, retryFailures = false)

        DurableFailureKind.entries.forEach { failure ->
            assertFalse("$failure was retried", policy.canRetry(mockk(), split.scheduled.policy.params, failure))
        }
    }

    // ---- deadlines ----

    /**
     * Nothing changes on a quiet chain, so waiting for the next look would notice a deadline only whenever the
     * call happens to return. The policy wakes at the deadline instead.
     */
    @Test
    fun `a deadline that passes on a quiet chain is noticed without a new look`() = runTest {
        val split = splitOf(1, retryUntil = WINDOW_OPEN + QUIET_DEADLINE)
        givenLedgerRecords(split)
        every { timeProvider.now() } answers {
            if (testScheduler.currentTime >= QUIET_DEADLINE.inWholeMilliseconds) WINDOW_CLOSED else WINDOW_OPEN
        }
        givenChain(look())

        val outcome = prepare(split)

        assertGaveUp(outcome, split)
        assertTrue(
            "the deadline was noticed only at ${testScheduler.currentTime}ms",
            testScheduler.currentTime < IDLE_LIMIT.inWholeMilliseconds,
        )
    }

    // ---- harness ----

    private suspend fun prepare(vararg splits: ScheduledSplit): Map<DurableTxId, SubmissionPreparation> =
        policy.prepareSubmission(splits.map { it.scheduled }).getOrThrow()

    private fun assertBuilt(outcome: Map<DurableTxId, SubmissionPreparation>, split: ScheduledSplit) {
        val ready = outcome[split.id]
        assertTrue("expected ${split.id} to be built but was $ready", ready is SubmissionPreparation.Ready)
        assertEquals(builtFor[split.input], (ready as SubmissionPreparation.Ready).extrinsic)
    }

    private fun assertLeftWaiting(outcome: Map<DurableTxId, SubmissionPreparation>, split: ScheduledSplit) {
        assertTrue("expected ${split.id} to keep waiting but was ${outcome[split.id]}", split.id !in outcome)
    }

    private fun assertGaveUp(outcome: Map<DurableTxId, SubmissionPreparation>, split: ScheduledSplit) {
        assertEquals(SubmissionPreparation.GiveUp, outcome[split.id])
    }

    private fun verifyNothingBuilt() {
        coVerify(exactly = 0) { splitExtrinsicBuilder.build(any(), any(), any()) }
    }

    private fun givenWindowClosed() {
        every { timeProvider.now() } returns WINDOW_CLOSED
    }

    private class ScheduledSplit(
        val scheduled: ScheduledDurableTx,
        val inputCoin: Coin,
        val outputCoins: List<Coin>,
    ) {
        val id: DurableTxId get() = scheduled.id
        val input: AccountId get() = inputCoin.accountId
    }

    /** One coin split into two, every coin with its own key; a seed keeps different splits' coins apart. */
    private fun splitOf(seed: Int, retryUntil: Instant = RETRY_UNTIL, retryFailures: Boolean = true): ScheduledSplit {
        val input = coinOf(seed * 10)
        val outputs = listOf(coinOf(seed * 10 + 1), coinOf(seed * 10 + 2))
        knownCoins += input
        knownCoins += outputs

        val scheduled = ScheduledDurableTx(
            id = DurableTxId(seed.toLong()),
            domainId = COINAGE_DOMAIN,
            groupId = GROUP,
            policy = CoinageSubmissionParams.splitPolicy(TransferSubmissionParams(retryUntil, retryFailures)),
        )

        return ScheduledSplit(scheduled, input, outputs)
    }

    private fun coinOf(item: Int) = Coin(
        derivationIndex = testKey(item),
        valueExponent = ValueExponent(3),
        age = Coin.Age.Unknown,
        isOnChain = false,
        accountId = byteArrayOf(item.toByte()).toDataByteArray(),
        provenance = CoinProvenance.UNKNOWN,
    )

    private fun givenLedgerRecords(vararg splits: ScheduledSplit) {
        coEvery { assetLedger.assetsOf(any()) } returns Result.success(
            splits.associate { split ->
                split.id to EntryAssets(
                    inputs = listOf(split.inputCoin.asLedgerAsset()),
                    outputs = split.outputCoins.map { it.asLedgerAsset() },
                )
            }
        )
    }

    private fun Coin.asLedgerAsset() = LedgerAsset(CoinageAssetKind.COIN, OwnAsset.Coin(derivationIndex), accountId)

    private sealed interface ChainStep

    private class Look(val read: Result<List<AccountId>>) : ChainStep

    private class Pause(val duration: Duration) : ChainStep

    private fun look(vararg present: AccountId): ChainStep = Look(Result.success(present.toList()))

    private fun failedRead(): ChainStep = Look(Result.failure(IllegalStateException("node unreachable")))

    private fun after(duration: Duration): ChainStep = Pause(duration)

    /** One emission per look, and a subscription that stays open after the last one. */
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
        val GROUP = OperationGroupId("chat-send")

        val RETRY_UNTIL = Instant.fromEpochSeconds(1_000)
        val WINDOW_OPEN = Instant.fromEpochSeconds(500)
        val WINDOW_CLOSED = Instant.fromEpochSeconds(1_500)

        val QUIET_DEADLINE = 60.seconds
        val IDLE_LIMIT = 5.minutes
    }
}
