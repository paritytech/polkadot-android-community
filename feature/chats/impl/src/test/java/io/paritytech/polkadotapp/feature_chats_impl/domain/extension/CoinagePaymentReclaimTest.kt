package io.paritytech.polkadotapp.feature_chats_impl.domain.extension

import io.mockk.coVerify
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessage.Content.CoinagePayment
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinagePaymentStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CoinagePaymentReclaimTest : CoinagePaymentProcessingExtensionTestBase() {
    @Test
    fun `an incoming payment whose coins are gone after its window went back to the sender`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenClaimReports(CoinageTransferDetection.Absent) { awaitCancellation() }

        val message = incomingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.ReturnedToSender)) }
        coVerify(exactly = 0) { context.markMessageProcessed(any(), any()) }
    }

    @Test
    fun `an incoming payment whose coins are not there yet inside its window still reads as receiving`() = runTest {
        givenClaimReports(CoinageTransferDetection.Absent) { awaitCancellation() }

        val message = incomingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.Detecting)) }
        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.ReturnedToSender)) }
    }

    @Test
    fun `an incoming payment still on chain after its window keeps reading as receiving`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenClaimReports(CoinageTransferDetection.Claiming) { awaitCancellation() }

        val message = incomingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.Detected(FULL))) }
        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.ReturnedToSender)) }
    }

    @Test
    fun `an incoming payment claimed after its window reads as received`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenClaimReports(
            CoinageTransferDetection.Absent,
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

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.ReturnedToSender)) }
        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.FailedTransfer)) }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    @Test
    fun `an incoming payment whose coins are gone turns gone back as its window closes`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 6.hours - 1.minutes
        givenClaimReports(CoinageTransferDetection.Absent) { awaitCancellation() }

        val message = incomingPayment()
        startWork(message, scheduler = testScheduler)

        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.ReturnedToSender)) }

        advanceTimeBy(2.minutes)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.ReturnedToSender)) }
    }

    @Test
    fun `an outgoing payment is offered back as its window closes`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 6.hours - 1.minutes
        givenCoins(CoinagePaymentStatus.AwaitingClaim)

        val message = outgoingPayment()
        startWork(message, scheduler = testScheduler)

        coVerify(exactly = 0) { context.modifyMessage(any(), any(), paymentWith(CoinagePayment.Status.Unclaimed)) }

        advanceTimeBy(2.minutes)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.Unclaimed)) }
    }

    @Test
    fun `an outgoing payment nobody claimed in time is offered back`() = runTest {
        now = Instant.fromEpochMilliseconds(SENT_AT) + 7.hours
        givenCoins(CoinagePaymentStatus.AwaitingClaim, CoinagePaymentStatus.AwaitingClaim)

        val message = outgoingPayment()
        startWork(message)

        coVerify { context.modifyMessage(message.chatId, message.id, paymentWith(CoinagePayment.Status.Unclaimed)) }
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
}
