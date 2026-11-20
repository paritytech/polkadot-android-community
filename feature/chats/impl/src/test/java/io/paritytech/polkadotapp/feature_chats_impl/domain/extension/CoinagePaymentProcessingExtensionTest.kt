package io.paritytech.polkadotapp.feature_chats_impl.domain.extension

import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.chains.util.Sr25519SecretKey
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.extension.ChatExtensionContext
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageOrigin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinProvenance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstallationId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageKeyIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.deriveKeypair
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ClaimReceivedCoinsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinagePaymentState
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinagePaymentStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinagePaymentStatusUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ReclaimHandedOffCoinsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import timber.log.Timber
import java.math.BigInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CoinagePaymentProcessingExtensionTest {
    private val paymentStatusUseCase: CoinagePaymentStatusUseCase = mockk()
    private val claimReceivedCoinsUseCase: ClaimReceivedCoinsUseCase = mockk()
    private val balanceConverterUseCase: CoinageBalanceConverterUseCase = mockk()
    private val reclaimHandedOffCoinsUseCase: ReclaimHandedOffCoinsUseCase = mockk()

    private var now = Instant.fromEpochMilliseconds(SENT_AT) + 1.hours
    private var failedReclaims = emptySet<AccountId>()

    private val extension = CoinagePaymentProcessingExtension(
        paymentStatusUseCase = paymentStatusUseCase,
        claimReceivedCoinsUseCase = claimReceivedCoinsUseCase,
        balanceConverterUseCase = balanceConverterUseCase,
        reclaimHandedOffCoinsUseCase = reclaimHandedOffCoinsUseCase,
        timeProvider = mockk<TimeProvider> { every { now() } answers { now } },
    )

    @Before
    fun stubReclaims() {
        every { reclaimHandedOffCoinsUseCase.subscribeFailedReclaims(any()) } answers { flowOf(failedReclaims) }
    }

    /** Stands in for the JNI binding, which refuses a secret of the wrong length and any it cannot load. */
    @Before
    fun mockDerivation() {
        mockkStatic(DERIVATION_FILE)
        every { any<CoinPrivateKey>().deriveKeypair() } answers {
            val key = firstArg<CoinPrivateKey>()
            bindingFailureFor(key)?.let { throw it }
            mockk<Sr25519Keypair> { every { publicKey } returns key.value }
        }
    }

    @After
    fun unmockDerivation() = unmockkStatic(DERIVATION_FILE)

    private val context: ChatExtensionContext = mockk(relaxed = true)
    private lateinit var scope: CoroutineScope

    @get:Rule
    val failureLog = FailureLog()

    @After
    fun stopWork() {
        if (::scope.isInitialized) scope.cancel()
    }

    /**
     * The claim use case owns when to stop: it retries until every coin is claimed or the caller's window
     * closes, so its completion — not any status it reported on the way — is what finishes the message.
     */
    @Test
    fun `an incoming payment is closed once its claim is done`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val message = incomingPayment()
        startWork(message)

        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    /**
     * Every claim failed and the window has closed, so the use case gave up. The message is finished even
     * though no money arrived — there is nothing left to try, and leaving it open would retry it forever.
     */
    @Test
    fun `an incoming payment whose claims all failed is closed once the claim gives up`() = runTest {
        givenClaimReports(CoinageTransferDetection.NotClaimed)

        val message = incomingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.FailedTransfer)) }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    @Test
    fun `claims are registered under the message's group with no deadline`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val message = incomingPayment()
        startWork(message)

        coVerify {
            claimReceivedCoinsUseCase.claim(any(), CoinageOperationGroupId("chat-claim:${message.id}"), Instant.DISTANT_FUTURE)
        }
    }

    /** What the claim reports on the way is shown, whether or not it is the last thing it will say. */
    @Test
    fun `the message shows what the claim reports as it goes`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claiming)

        val message = incomingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status == CoinagePayment.Status.Detected(FULL) },
            )
        }
    }

    @Test
    fun `an incoming payment held up by a failed claim shows what landed and stays open`() = runTest {
        givenClaimReports(CoinageTransferDetection.ClaimingRest(PARTIAL)) { awaitCancellation() }

        val message = incomingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.PartiallyClaimed(PARTIAL)))
        }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    /**
     * An app killed mid-claim: the scope goes and the flow never completes. The message stays unprocessed so
     * the next launch picks it up again, which is the only thing that makes a claim survive a restart.
     */
    @Test
    fun `a claim cut short by shutdown leaves the payment open`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claiming) { awaitCancellation() }

        startWork(incomingPayment())
        scope.cancel()

        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    /**
     * Claiming ended with only part of the payment ours. The message reports what landed, which is what the
     * bubble compares against the amount sent to show the shortfall — the one place that comparison is
     * allowed to happen.
     */
    @Test
    fun `an incoming payment that ended short reports what landed`() = runTest {
        givenClaimReports(CoinageTransferDetection.ClaimedPartially(PARTIAL))

        val message = incomingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status == CoinagePayment.Status.Transferred(PARTIAL) },
            )
        }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    /** A payment that lands while the app is open is picked up from the live feed, not only at startup. */
    @Test
    fun `a payment arriving while the app is running is processed`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val message = incomingPayment()
        startWork(arriving = listOf(message))

        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    // ---- outgoing ----

    /**
     * The peer has taken some of the coins and the rest are still sitting on chain.
     *
     * The sender is told the payment is awaiting claim, for the whole amount. PartiallyClaimed belongs to
     * claiming, which only the receiving side does — a sender reporting "claimed 0.12 of 1.00" would be
     * announcing a shortfall that is entirely the peer's to resolve, and that resolves itself the moment
     * they claim the rest.
     */
    @Test
    fun `an outgoing payment part-taken by the peer still reads as awaiting claim`() = runTest {
        givenPeerHasTaken(claimed = 1, awaiting = 1, detecting = 0, failed = 0, finalized = true)

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status is CoinagePayment.Status.Detected },
            )
        }
        coVerify(exactly = 0) {
            context.modifyMessage(
                any(),
                any(),
                match { it is CoinagePayment && it.status is CoinagePayment.Status.PartiallyClaimed },
            )
        }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    /** Nothing the sender minted is on chain yet, so the payment still reads as being sent. */
    @Test
    fun `an outgoing payment whose coins have not appeared yet still reads as sending`() = runTest {
        givenPeerHasTaken(claimed = 0, awaiting = 0, detecting = 2, failed = 0, finalized = true)

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status == CoinagePayment.Status.Detecting },
            )
        }
    }

    /**
     * A large payment is minted by several transactions, so its coins appear on chain in waves. A quick peer
     * can take every coin that exists while the rest are still being minted.
     *
     * The payment is not over: reporting it as transferred for what has been taken so far makes the bubble
     * compare a fraction against the amount sent and announce a shortfall — a 35.1 payment read as "claimed
     * 2.xx" for the half-minute before the remaining transactions landed.
     */
    @Test
    fun `an outgoing payment is not called transferred while coins are still being minted`() = runTest {
        givenPeerHasTaken(claimed = 2, awaiting = 0, detecting = 3, failed = 0, finalized = true)

        val message = outgoingPayment()
        startWork(message)

        coVerify(exactly = 0) {
            context.modifyMessage(
                any(),
                any(),
                match { it is CoinagePayment && it.status is CoinagePayment.Status.Transferred },
            )
        }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    /**
     * Nothing is pending any more: what was minted has been taken and the rest never existed. That really is
     * the end, and the shortfall is real.
     */
    @Test
    fun `an outgoing payment settles short when the coins that failed will never exist`() = runTest {
        givenPeerHasTaken(claimed = 2, awaiting = 0, detecting = 0, failed = 3, finalized = true)

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status is CoinagePayment.Status.Transferred },
            )
        }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    /**
     * The peer has taken every coin, as far as the best chain shows, but no claim has finalized.
     *
     * Both halves matter and they differ. The message says so at once — making the sender wait a whole
     * finality to be told their payment landed is the latency this design exists to avoid. But it stays
     * open, because a fork can still put those coins back and nothing watches a closed message.
     */
    @Test
    fun `an outgoing payment reads as claimed at once but is not closed until that is beyond recall`() = runTest {
        givenPeerHasTaken(claimed = 2, awaiting = 0, detecting = 0, failed = 0, finalized = false)

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status is CoinagePayment.Status.Transferred },
            )
        }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    /** Nothing has been taken yet and some coins never will be: still sending, for the ones that can arrive. */
    @Test
    fun `an outgoing payment still being minted alongside coins that failed reads as sending`() = runTest {
        givenPeerHasTaken(claimed = 0, awaiting = 0, detecting = 2, failed = 3, finalized = true)

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status == CoinagePayment.Status.Detecting },
            )
        }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    /**
     * Nothing is known about the payment's coins yet — no rows to read.
     *
     * That is ignorance, not completion. Closing on it settles a payment for good on the one reading that
     * says nothing at all, and nothing watches a closed message afterwards.
     */
    @Test
    fun `an outgoing payment is not closed while nothing is known about its coins`() = runTest {
        givenPeerHasTaken(claimed = 0, awaiting = 0, detecting = 0, failed = 0, finalized = true)

        val message = outgoingPayment()
        startWork(message)

        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status == CoinagePayment.Status.Detecting },
            )
        }
    }

    /** Every coin taken: the payment is done and the message never needs looking at again. */
    @Test
    fun `an outgoing payment is closed once the peer has taken every coin`() = runTest {
        givenPeerHasTaken(claimed = 2, awaiting = 0, detecting = 0, failed = 0, finalized = true)

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status is CoinagePayment.Status.Transferred },
            )
        }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    /**
     * The coins were never minted, so the keys the peer holds control nothing. Nothing will change that, so
     * the message is closed rather than watched forever.
     */
    @Test
    fun `an outgoing payment whose coins never existed is closed as failed`() = runTest {
        givenPeerHasTaken(claimed = 0, awaiting = 0, detecting = 0, failed = 2, finalized = true)

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status is CoinagePayment.Status.FailedDetection },
            )
        }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    /**
     * The coins cannot be valued — the conversion needs chain metadata, which may not be to hand.
     *
     * The payment still reports its stage, with an amount of zero. Failing to say anything would leave the
     * sender staring at a message that never moves off "sending".
     */
    @Test
    fun `an outgoing payment still reports its stage when the coins cannot be valued`() = runTest {
        givenPeerHasTaken(claimed = 0, awaiting = 2, detecting = 0, failed = 0, finalized = true)
        coEvery { balanceConverterUseCase.create() } returns Result.failure(IllegalStateException("no metadata"))

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status == CoinagePayment.Status.Detected(Balance.ZERO) },
            )
        }
    }

    @Test
    fun `an incoming payment whose coins are gone after its window went back to the sender`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenClaimReports(CoinageTransferDetection.NoCoinsOnChain) { awaitCancellation() }

        val message = incomingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.ClaimWindowExpired)) }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    @Test
    fun `an incoming payment whose coins are not there yet inside its window still reads as receiving`() = runTest {
        givenClaimReports(CoinageTransferDetection.NoCoinsOnChain) { awaitCancellation() }

        val message = incomingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.Detecting)) }
        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.ClaimWindowExpired)) }
    }

    @Test
    fun `an incoming payment still on chain after its window keeps reading as receiving`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenClaimReports(CoinageTransferDetection.Claiming) { awaitCancellation() }

        val message = incomingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.Detected(FULL))) }
        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.ClaimWindowExpired)) }
    }

    @Test
    fun `an incoming payment claimed after its window reads as received`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenClaimReports(
            CoinageTransferDetection.NoCoinsOnChain,
            CoinageTransferDetection.Claiming,
            CoinageTransferDetection.Claimed(FULL, finalized = true),
        )

        val message = incomingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.Transferred(FULL))) }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    @Test
    fun `an incoming payment whose claim gave up after its window is closed as gone back`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenClaimReports(CoinageTransferDetection.NotClaimed)

        val message = incomingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.ClaimWindowExpired)) }
        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.FailedTransfer)) }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    @Test
    fun `an incoming payment whose coins are gone turns gone back as its window closes`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 6.hours - 1.minutes
        givenClaimReports(CoinageTransferDetection.NoCoinsOnChain) { awaitCancellation() }

        val message = incomingPayment()
        startWork(message, scheduler = testScheduler)

        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.ClaimWindowExpired)) }

        advanceTimeBy(2.minutes)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.ClaimWindowExpired)) }
    }

    @Test
    fun `an outgoing payment is offered back as its window closes`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 6.hours - 1.minutes
        givenCoins(CoinagePaymentStatus.AwaitingClaim)

        val message = outgoingPayment()
        startWork(message, scheduler = testScheduler)

        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.ClaimWindowExpired)) }

        advanceTimeBy(2.minutes)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.ClaimWindowExpired)) }
    }

    @Test
    fun `an outgoing payment nobody claimed in time is offered back`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenCoins(CoinagePaymentStatus.AwaitingClaim, CoinagePaymentStatus.AwaitingClaim)

        val message = outgoingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.ClaimWindowExpired)) }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    @Test
    fun `an outgoing payment being taken back reads as reclaiming`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenCoins(CoinagePaymentStatus.Reclaiming, CoinagePaymentStatus.AwaitingClaim)

        val message = outgoingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.Reclaiming)) }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    @Test
    fun `an outgoing payment reclaimed at best reads as reclaimed but is not closed`() = runTest {
        givenCoins(CoinagePaymentStatus.Reclaimed(finalized = false), CoinagePaymentStatus.Reclaimed(finalized = false))

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status is CoinagePayment.Status.Reclaimed },
            )
        }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    @Test
    fun `an outgoing payment reclaimed beyond recall is closed as reclaimed`() = runTest {
        givenCoins(CoinagePaymentStatus.Reclaimed(finalized = true), CoinagePaymentStatus.Reclaimed(finalized = true))

        val message = outgoingPayment()
        startWork(message)

        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status is CoinagePayment.Status.Reclaimed },
            )
        }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    @Test
    fun `an outgoing payment whose reclaim failed offers to try again`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        failedReclaims = setOf(accountId(0))
        givenCoins(CoinagePaymentStatus.AwaitingClaim)

        val message = outgoingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.ReclaimFailed)) }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
        coVerify { reclaimHandedOffCoinsUseCase.subscribeFailedReclaims(CoinageOperationGroupId("chat-reclaim:${message.id}")) }
    }

    @Test
    fun `an outgoing payment the peer claimed after a failed reclaim reads as received`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        failedReclaims = setOf(accountId(0))
        givenCoins(CoinagePaymentStatus.Claimed(finalized = true))

        val message = outgoingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.Transferred(PARTIAL))) }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    @Test
    fun `an incoming payment with no good coin key is closed as failed without a claim`() = runTest {
        val message = incomingPayment(
            coinKeys = listOf(
                ByteArray(0),
                ByteArray(Sr25519SecretKey.SIZE_BYTES - 1),
                ByteArray(Sr25519SecretKey.SIZE_BYTES + 1),
                REFUSED_KEY.value,
            ),
        )
        startWork(message)

        verifyClosedAsFailed(message, CoinagePayment.Status.FailedTransfer)
        coVerify(exactly = 0) { claimReceivedCoinsUseCase.claim(any(), any(), any()) }
        verify(exactly = 0) { match<CoinPrivateKey> { it.value.size != Sr25519SecretKey.SIZE_BYTES }.deriveKeypair() }
    }

    @Test
    fun `an outgoing payment with no good coin key is closed as failed`() = runTest {
        val message = outgoingPayment(coinKeys = listOf(ByteArray(0), REFUSED_KEY.value))
        startWork(message)

        verifyClosedAsFailed(message, CoinagePayment.Status.FailedDetection)
        verify(exactly = 0) { paymentStatusUseCase.subscribeStatuses(any()) }
    }

    @Test
    fun `only the good coin keys of a payment are claimed`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val good = coinKey(1)
        startWork(incomingPayment(coinKeys = listOf(ByteArray(0), REFUSED_KEY.value, good)))

        coVerify(exactly = 1) { claimReceivedCoinsUseCase.claim(listOf(good.toDataByteArray()), any(), any()) }
    }

    @Test
    fun `a payment that fails while processing is left open and later payments are still processed`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))
        val failedClaim = incomingPayment(id = "failed-claim")
        every {
            claimReceivedCoinsUseCase.claim(any(), CoinageOperationGroupId("chat-claim:${failedClaim.id}"), any())
        } returns flow {
            emit(Result.success(CoinageTransferDetection.Detecting))
            throw IllegalStateException("ledger unavailable")
        }

        val unreadableStatus = outgoingPayment(id = "unreadable-status")
        every { paymentStatusUseCase.subscribeStatuses(any()) } returns flow { throw IllegalStateException("ledger unavailable") }

        val unwritableStatus = incomingPayment(id = "unwritable-status", coinKeys = listOf(ByteArray(0)))
        coEvery { context.modifyMessage(any(), unwritableStatus.id, any()) } throws IllegalStateException("db")

        val valid = incomingPayment()
        startWork(arriving = listOf(failedClaim, unreadableStatus, unwritableStatus, valid))

        verifyLeftOpen(failedClaim, unreadableStatus, unwritableStatus, stillProcessing = valid)
    }

    @Test
    fun `a payment is left open when the binding itself fails`() = runTest {
        val message = incomingPayment(coinKeys = listOf(BROKEN_BINDING_KEY.value))
        startWork(message)

        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
        coVerify(exactly = 0) { context.modifyMessage(any(), any(), any()) }
        failureLog.expectedIds += message.id
    }

    @Test
    fun `an outgoing payment loads each coin key once`() = runTest {
        givenPeerHasTaken(claimed = 2, awaiting = 0, detecting = 0, failed = 0, finalized = true)

        val keys = listOf(coinKey(1), coinKey(2))
        startWork(outgoingPayment(coinKeys = keys))

        keys.forEach { key -> verify(exactly = 1) { key.toDataByteArray().deriveKeypair() } }
    }

    // ---- harness ----

    private fun startWork(
        vararg unprocessed: ChatMessage,
        arriving: List<ChatMessage> = emptyList(),
        scheduler: TestCoroutineScheduler? = null,
    ) {
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))

        every { context.scope } returns TestComputationalScope(scope)
        every { context.subscribeNewMessages(any(), any()) } returns arriving.asFlow()
        coEvery { context.getUnprocessedMessages(any(), any()) } returns unprocessed.toList()

        with(context) { extension.startGlobalWork() }
    }

    private fun givenClaimReports(
        vararg reports: CoinageTransferDetection,
        andThen: suspend () -> Unit = {},
    ) {
        every { claimReceivedCoinsUseCase.claim(any(), any(), any()) } answers {
            val keys = firstArg<List<CoinPrivateKey>>()
            flow {
                keys.forEach { it.deriveKeypair() }
                reports.forEach { emit(Result.success(it)) }
                andThen()
            }
        }
    }

    /**
     * The whole mix of coins behind one payment, none of it defaulted.
     *
     * What the message shows and whether it may be closed are decided by *which kinds* are present, and the
     * interesting cases are the mixtures. A default would let a test leave one kind unstated and quietly
     * land in a different case than the one it was written for.
     */
    private fun givenPeerHasTaken(
        claimed: Int,
        awaiting: Int,
        detecting: Int,
        failed: Int,
        finalized: Boolean,
    ) {
        givenCoins(
            *Array(claimed) { CoinagePaymentStatus.Claimed(finalized) },
            *Array(awaiting) { CoinagePaymentStatus.AwaitingClaim },
            *Array(failed) { CoinagePaymentStatus.Failed },
            *Array(detecting) { CoinagePaymentStatus.Detecting },
        )
    }

    private fun givenCoins(vararg statuses: CoinagePaymentStatus) {
        val states = statuses.withIndex().associate { (index, status) -> accountId(index) to stateOf(accountId(index), status) }

        every { paymentStatusUseCase.subscribeStatuses(any()) } returns flow {
            emit(states)
            awaitCancellation()
        }
        // formatCoinsToBalance is a top-level extension over this member, so stubbing the member is enough.
        coEvery { balanceConverterUseCase.create() } returns Result.success(
            mockk { every { formatExponentToBalance(any()) } returns PARTIAL }
        )
    }

    private fun verifyClosedAsFailed(message: ChatMessage, status: CoinagePayment.Status) {
        coVerify(exactly = 1) {
            context.modifyMessage(message.chatId, message.id, match { it is CoinagePayment && it.status == status })
        }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    private fun verifyLeftOpen(vararg messages: ChatMessage, stillProcessing: ChatMessage) {
        messages.forEach { message ->
            coVerify(exactly = 0) { context.markMessageProcessed(message.chatId, message.id) }
        }
        coVerify(exactly = 1) { context.markMessageProcessed(stillProcessing.chatId, stillProcessing.id) }
        failureLog.expectedIds += messages.map { it.id }
    }

    private fun accountId(seed: Int): AccountId = byteArrayOf(seed.toByte()).toDataByteArray()

    private fun stateOf(accountId: AccountId, status: CoinagePaymentStatus) = CoinagePaymentState(
        coin = Coin(derivationIndex = CoinageKeyIndex(CoinageInstallationId(ByteArray(CoinageInstallationId.SIZE_BYTES).toDataByteArray()), 0), valueExponent = ValueExponent(3), age = Coin.Age.Unknown, isOnChain = false, accountId = accountId, provenance = CoinProvenance.UNKNOWN),
        status = status,
    )

    private fun MockKMatcherScope.paymentWith(status: CoinagePayment.Status) =
        match<ChatMessage.Content> { it is CoinagePayment && it.status == status }

    private fun outgoingPayment(
        id: String = "message-1",
        coinKeys: List<ByteArray> = listOf(coinKey(1), coinKey(2)),
    ) = incomingPayment(id = id, coinKeys = coinKeys).copy(origin = ChatMessageOrigin.User)

    private fun coinKey(seed: Int) = ByteArray(Sr25519SecretKey.SIZE_BYTES) { seed.toByte() }

    private fun incomingPayment(
        id: String = "message-1",
        coinKeys: List<ByteArray> = listOf(coinKey(1), coinKey(2)),
    ) = ChatMessage(
        id = id,
        chatId = ChatId.fromContact(byteArrayOf(9).intoAccountId()),
        timestamp = SENT_AT,
        origin = ChatMessageOrigin.Contact(byteArrayOf(9).intoAccountId()),
        content = CoinagePayment(
            totalValue = FULL,
            coinKeys = coinKeys,
            status = CoinagePayment.Status.Detecting,
        ),
        status = ChatMessage.Status.IS_SENT,
        sortOrder = null,
    )

    /**
     * [ComputationalScope]'s own factory returns a value class, which a mocked getter hands back erased to
     * the scope it wraps. A plain implementation survives the boundary.
     */
    private class TestComputationalScope(scope: CoroutineScope) : ComputationalScope, CoroutineScope by scope

    private companion object {
        const val SENT_AT = 1_700_000_000_000L
        const val DERIVATION_FILE = "io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferMemoKt"

        val FULL = BigInteger.valueOf(100).intoBalance()
        val PARTIAL = BigInteger.valueOf(12).intoBalance()
    }
}

private val REFUSED_KEY = ByteArray(Sr25519SecretKey.SIZE_BYTES) { -1 }.toDataByteArray()
private val BROKEN_BINDING_KEY = ByteArray(Sr25519SecretKey.SIZE_BYTES) { -3 }.toDataByteArray()
private val PROCESSING_FAILED = Regex("^Payment processing failed message=(\\S+)")

private fun bindingFailureFor(key: CoinPrivateKey): Throwable? = when {
    key.value.size != Sr25519SecretKey.SIZE_BYTES || key == REFUSED_KEY -> Exception("Invalid secret")
    key == BROKEN_BINDING_KEY -> LinkageError("binding unavailable")
    else -> null
}

class FailureLog : ExternalResource() {
    val expectedIds = mutableListOf<String>()
    private val failedIds = mutableListOf<String>()

    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            PROCESSING_FAILED.find(message)?.let { failedIds += it.groupValues[1] }
        }
    }

    override fun before() = Timber.plant(tree)

    override fun after() {
        Timber.uproot(tree)
        assertEquals(expectedIds.sorted(), failedIds.sorted())
    }
}
