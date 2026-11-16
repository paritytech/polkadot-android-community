package io.paritytech.polkadotapp.feature_chats_impl.domain.extension

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.util.Sr25519SecretKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.deriveKeypair
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CoinagePaymentProcessingExtensionTest : CoinagePaymentProcessingExtensionTestBase() {
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
    fun `an incoming payment with no usable coin key is closed as failed without a claim`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val unusable = incomingPayment(id = "unusable", coinKeys = listOf(ByteArray(0)))
        val valid = incomingPayment()
        startWork(arriving = listOf(unusable, valid))

        verifyClosedAsFailed(unusable, CoinagePayment.Status.FailedTransfer)
        coVerify(exactly = 1) { claimReceivedCoinsUseCase.claim(any(), any(), any()) }
        coVerify(exactly = 1) { context.markMessageProcessed(valid.chatId, valid.id) }
    }

    @Test
    fun `a stored incoming payment with no usable coin key is closed as failed at launch`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val unusable = incomingPayment(id = "unusable", coinKeys = listOf(ByteArray(0)))
        val valid = incomingPayment()
        startWork(unusable, valid)

        verifyClosedAsFailed(unusable, CoinagePayment.Status.FailedTransfer)
        coVerify(exactly = 1) { claimReceivedCoinsUseCase.claim(any(), any(), any()) }
        coVerify(exactly = 1) { context.markMessageProcessed(valid.chatId, valid.id) }
    }

    @Test
    fun `only the usable coin keys of a payment are claimed`() = runTest {
        givenClaimReports(CoinageTransferDetection.ClaimedPartially(PARTIAL))

        val usable = coinKey(1)
        val message = incomingPayment(coinKeys = listOf(ByteArray(0), ByteArray(3), REFUSED_KEY.value, usable))
        startWork(message)

        coVerify(exactly = 1) {
            claimReceivedCoinsUseCase.claim(listOf(usable.toDataByteArray()), any(), any())
        }
        verify(exactly = 0) { match<CoinPrivateKey> { it.value.size != Sr25519SecretKey.SIZE_BYTES }.deriveKeypair() }
        coVerify {
            context.modifyMessage(
                message.chatId,
                message.id,
                match { it is CoinagePayment && it.status == CoinagePayment.Status.Transferred(PARTIAL) },
            )
        }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    @Test
    fun `a payment whose coin keys are all usable claims every one of them`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val keys = listOf(coinKey(1), coinKey(2), coinKey(3))
        startWork(incomingPayment(coinKeys = keys))

        coVerify(exactly = 1) {
            claimReceivedCoinsUseCase.claim(keys.map { it.toDataByteArray() }, any(), any())
        }
    }

    @Test
    fun `a claim that fails leaves its payment open and later payments still processed`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))
        val broken = incomingPayment(id = "broken")
        every {
            claimReceivedCoinsUseCase.claim(any(), CoinageOperationGroupId("chat-claim:${broken.id}"), any())
        } returns flow {
            emit(CoinageTransferDetection.Detecting)
            throw IllegalStateException("ledger unavailable")
        }

        val valid = incomingPayment()
        startWork(arriving = listOf(broken, valid))

        verifyLeftOpen(broken, stillProcessing = valid)
    }

    @Test
    fun `an outgoing payment whose status cannot be read is left open`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))
        every { paymentStatusUseCase.subscribeStatuses(any()) } returns flow { throw IllegalStateException("ledger unavailable") }

        val outgoing = outgoingPayment()
        val valid = incomingPayment(id = "incoming")
        startWork(arriving = listOf(outgoing, valid))

        verifyLeftOpen(outgoing, stillProcessing = valid)
    }

    @Test
    fun `an outgoing payment with no usable coin key is closed as failed`() = runTest {
        val message = outgoingPayment(coinKeys = listOf(ByteArray(0)))
        startWork(message)

        verifyClosedAsFailed(message, CoinagePayment.Status.FailedDetection)
        verify(exactly = 0) { paymentStatusUseCase.subscribeStatuses(any()) }
    }

    @Test
    fun `a payment with no coin keys at all is closed as failed without a claim`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val message = incomingPayment(coinKeys = emptyList())
        startWork(message)

        verifyClosedAsFailed(message, CoinagePayment.Status.FailedTransfer)
        coVerify(exactly = 0) { claimReceivedCoinsUseCase.claim(any(), any(), any()) }
    }

    @Test
    fun `coin keys one byte either side of a secret's length are never derived`() = runTest {
        val message = incomingPayment(
            coinKeys = listOf(ByteArray(Sr25519SecretKey.SIZE_BYTES - 1), ByteArray(Sr25519SecretKey.SIZE_BYTES + 1)),
        )
        startWork(message)

        verifyClosedAsFailed(message, CoinagePayment.Status.FailedTransfer)
        coVerify(exactly = 0) { claimReceivedCoinsUseCase.claim(any(), any(), any()) }
        verify(exactly = 0) { match<CoinPrivateKey> { it.value.size != Sr25519SecretKey.SIZE_BYTES }.deriveKeypair() }
    }

    @Test
    fun `an outgoing payment whose only coin key cannot be loaded is closed as failed`() = runTest {
        val message = outgoingPayment(coinKeys = listOf(REFUSED_KEY.value))
        startWork(message)

        verifyClosedAsFailed(message, CoinagePayment.Status.FailedDetection)
        verify(exactly = 0) { paymentStatusUseCase.subscribeStatuses(any()) }
    }

    @Test
    fun `a status write that fails leaves its payment open`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val broken = incomingPayment(id = "broken")
        coEvery { context.modifyMessage(any(), broken.id, any()) } throws IllegalStateException("db")
        val valid = incomingPayment()
        startWork(arriving = listOf(broken, valid))

        verifyLeftOpen(broken, stillProcessing = valid)
    }

    @Test
    fun `a failed status write for a payment with no usable coin key does not escape`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val broken = incomingPayment(id = "broken", coinKeys = listOf(ByteArray(0)))
        coEvery { context.modifyMessage(any(), broken.id, any()) } throws IllegalStateException("db")
        val valid = incomingPayment()
        startWork(arriving = listOf(broken, valid))

        verifyLeftOpen(broken, stillProcessing = valid)
    }

    @Test
    fun `a coin key that fails after validation leaves its incoming payment open`() = runTest {
        givenClaimReports(CoinageTransferDetection.Claimed(FULL, finalized = true))

        val broken = incomingPayment(id = "broken", coinKeys = listOf(REFUSED_AFTER_PROBE.value))
        val valid = incomingPayment()
        startWork(arriving = listOf(broken, valid))

        verifyLeftOpen(broken, stillProcessing = valid)
    }

    @Test
    fun `an outgoing payment loads each coin key once`() = runTest {
        givenPeerHasTaken(claimed = 2, awaiting = 0, detecting = 0, failed = 0, finalized = true)

        val keys = listOf(coinKey(1), coinKey(2))
        startWork(outgoingPayment(coinKeys = keys))

        keys.forEach { key -> verify(exactly = 1) { key.toDataByteArray().deriveKeypair() } }
    }

    @Test
    fun `a payment is left open when the binding itself fails`() = runTest {
        val message = incomingPayment(coinKeys = listOf(BROKEN_BINDING_KEY.value))
        startWork(message)

        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
        coVerify(exactly = 0) { context.modifyMessage(any(), any(), any()) }
        failureLog.expectedIds += message.id
    }
}
