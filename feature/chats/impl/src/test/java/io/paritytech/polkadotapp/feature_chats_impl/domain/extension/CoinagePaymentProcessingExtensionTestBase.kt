package io.paritytech.polkadotapp.feature_chats_impl.domain.extension

import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import timber.log.Timber
import java.math.BigInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
abstract class CoinagePaymentProcessingExtensionTestBase {
    protected val paymentStatusUseCase: CoinagePaymentStatusUseCase = mockk()
    protected val claimReceivedCoinsUseCase: ClaimReceivedCoinsUseCase = mockk()
    protected val balanceConverterUseCase: CoinageBalanceConverterUseCase = mockk()
    protected val reclaimHandedOffCoinsUseCase: ReclaimHandedOffCoinsUseCase = mockk()

    protected var now = Instant.fromEpochMilliseconds(SENT_AT) + 1.hours
    protected var failedReclaims = emptySet<AccountId>()

    protected val extension = CoinagePaymentProcessingExtension(
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

    protected val context: ChatExtensionContext = mockk(relaxed = true)
    protected lateinit var scope: CoroutineScope

    @get:Rule
    val failureLog = FailureLog()

    @After
    fun stopWork() {
        if (::scope.isInitialized) scope.cancel()
    }

    protected fun startWork(
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

    protected fun givenClaimReports(
        vararg reports: CoinageTransferDetection,
        andThen: suspend () -> Unit = {},
    ) {
        every { claimReceivedCoinsUseCase.claim(any(), any(), any()) } answers {
            val keys = firstArg<List<CoinPrivateKey>>()
            flow {
                keys.forEach { it.deriveKeypair() }
                reports.forEach { emit(it) }
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
    protected fun givenPeerHasTaken(
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

    protected fun givenCoins(vararg statuses: CoinagePaymentStatus) {
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

    protected fun verifyClosedAsFailed(message: ChatMessage, status: CoinagePayment.Status) {
        coVerify(exactly = 1) {
            context.modifyMessage(message.chatId, message.id, match { it is CoinagePayment && it.status == status })
        }
        coVerify(exactly = 1) { context.markMessageProcessed(message.chatId, message.id) }
    }

    protected fun verifyLeftOpen(vararg messages: ChatMessage, stillProcessing: ChatMessage) {
        messages.forEach { message ->
            coVerify(exactly = 0) { context.markMessageProcessed(message.chatId, message.id) }
        }
        coVerify(exactly = 1) { context.markMessageProcessed(stillProcessing.chatId, stillProcessing.id) }
        failureLog.expectedIds += messages.map { it.id }
    }

    protected fun accountId(seed: Int): AccountId = byteArrayOf(seed.toByte()).toDataByteArray()

    protected fun stateOf(accountId: AccountId, status: CoinagePaymentStatus) = CoinagePaymentState(
        coin = Coin(derivationIndex = CoinageKeyIndex(CoinageInstallationId(ByteArray(CoinageInstallationId.SIZE_BYTES).toDataByteArray()), 0), valueExponent = ValueExponent(3), age = Coin.Age.Unknown, isOnChain = false, accountId = accountId, provenance = CoinProvenance.UNKNOWN),
        status = status,
    )

    protected fun MockKMatcherScope.paymentWith(status: CoinagePayment.Status) =
        match<ChatMessage.Content> { it is CoinagePayment && it.status == status }

    protected fun outgoingPayment(
        id: String = "message-1",
        coinKeys: List<ByteArray> = listOf(coinKey(1), coinKey(2)),
    ) = incomingPayment(id = id, coinKeys = coinKeys).copy(origin = ChatMessageOrigin.User)

    protected fun coinKey(seed: Int) = ByteArray(Sr25519SecretKey.SIZE_BYTES) { seed.toByte() }

    protected fun incomingPayment(
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

    protected companion object {
        const val SENT_AT = 1_700_000_000_000L
        const val DERIVATION_FILE = "io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferMemoKt"

        val FULL = BigInteger.valueOf(100).intoBalance()
        val PARTIAL = BigInteger.valueOf(12).intoBalance()
    }
}

internal val REFUSED_KEY = ByteArray(Sr25519SecretKey.SIZE_BYTES) { -1 }.toDataByteArray()
internal val BROKEN_BINDING_KEY = ByteArray(Sr25519SecretKey.SIZE_BYTES) { -3 }.toDataByteArray()
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
