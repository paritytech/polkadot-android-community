package io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.queue

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.WeightV2
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_balances_api.data.type.TokenBalanceType
import io.paritytech.polkadotapp.feature_balances_api.data.type.TokenBalanceTypeRegistry
import io.paritytech.polkadotapp.feature_balances_api.domain.model.TokenBalance
import io.paritytech.polkadotapp.feature_dotns_gateway_api.domain.model.DotNsNameReservedByOtherError
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.config.DotNsGatewayConfig
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCheckMemberInRingUseCase
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_pgas_api.domain.OnExistingAllocationStrategy
import io.paritytech.polkadotapp.feature_pgas_api.domain.PgasChainAssetProvider
import io.paritytech.polkadotapp.feature_pgas_api.domain.PgasClaimer
import io.paritytech.polkadotapp.feature_revive_api.ReviveContractApi
import io.paritytech.polkadotapp.feature_revive_api.ReviveContractReverted
import io.paritytech.polkadotapp.feature_revive_api.ReviveDryRun
import io.paritytech.polkadotapp.feature_revive_api.ReviveStorageDepositUnfunded
import io.paritytech.polkadotapp.feature_revive_api.toEvmAccountId
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicDispatch
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicExecutionResult
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.AccountFee
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigInteger

class DotNsReservationQueueClearerTest {
    private val queueReader: DotNsReservationQueueReader = mock()
    private val reviveContractApi: ReviveContractApi = mock()
    private val extrinsicService: ExtrinsicService = mock()
    private val accountRepository: AccountRepository = mock()
    private val pgasClaimer: PgasClaimer = mock()
    private val pgasChainAssetProvider: PgasChainAssetProvider = mock()
    private val tokenBalanceTypeRegistry: TokenBalanceTypeRegistry = mock()
    private val chain: Chain = mock()
    private val pgasBalanceType: TokenBalanceType = mock()
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase = mock()
    private val peopleCheckMemberInRingUseCase: PeopleCheckMemberInRingUseCase = mock()

    private val clearer = DotNsReservationQueueClearer(
        queueReader = queueReader,
        reviveContractApi = reviveContractApi,
        extrinsicService = extrinsicService,
        accountRepository = accountRepository,
        pgasClaimer = pgasClaimer,
        pgasChainAssetProvider = pgasChainAssetProvider,
        tokenBalanceTypeRegistry = tokenBalanceTypeRegistry,
        activePeopleCollectionUseCase = activePeopleCollectionUseCase,
        peopleCheckMemberInRingUseCase = peopleCheckMemberInRingUseCase
    )

    @Test
    fun `claims directly when no dead entry stands in front`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 0)

        assertSuccess(prepareClaim())
        verifyNothingSubmitted()
    }

    @Test
    fun `refuses the claim when another account holds the first active reservation`() = runBlocking<Unit> {
        withQueue(holder = OTHER_HOLDER, deadInFront = 6)

        val result = prepareClaim()

        assertTrue("expected DotNsNameReservedByOtherError but was ${result.exceptionOrNull()}", result.exceptionOrNull() is DotNsNameReservedByOtherError)
        verifyNothingSubmitted()
    }

    @Test
    fun `leaves a dead run the claim clears itself on dotNS 1_1_0`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 4)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))

        assertSuccess(prepareClaim())
        verifyNothingSubmitted()
    }

    @Test
    fun `clears a dead run longer than one gateway call clears on dotNS 1_1_0`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost()
        withPgasBalances(BigInteger.valueOf(1_000))
        withSuccessfulSubmission()

        assertSuccess(prepareClaim())
        verifyClearingSubmitted()
    }

    @Test
    fun `clears any dead entry in front on dotNS 1_0_0`() = runBlocking<Unit> {
        withQueue(holder = null, deadInFront = 1)
        withMaxQueueStepsPerCall(null)
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost()
        withPgasBalances(BigInteger.valueOf(1_000))
        withSuccessfulSubmission()

        assertSuccess(prepareClaim())
        verifyClearingSubmitted()
    }

    @Test
    fun `does not clear for an account other than the wallet`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(ByteArray(32) { 2 }.toDataByteArray())

        val result = prepareClaim()

        assertTrue("expected DotNsClaimantNotWalletError but was ${result.exceptionOrNull()}", result.exceptionOrNull() is DotNsClaimantNotWalletError)
        verifyNothingSubmitted()
    }

    // A PGAS claim maps only an account it creates, and an account holding PGAS already exists.
    @Test
    fun `does not clear from an existing wallet account without a Revive mapping`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost(mapped = listOf(false))
        withPgasBalances(BigInteger.valueOf(1_000))

        val result = prepareClaim()

        assertTrue("expected DotNsQueueClearAccountUnmappedError but was ${result.exceptionOrNull()}", result.exceptionOrNull() is DotNsQueueClearAccountUnmappedError)
        verifyNotDryRun()
        verifyNoPgasClaimed()
        verifyNothingSubmitted()
    }

    // The chain maps an account when it creates it, and a PGAS claim creates one it has never seen.
    @Test
    fun `claims PGAS first for a wallet account the chain has never seen`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost(mapped = listOf(false, true))
        withPgasBalances(BigInteger.ZERO, BigInteger.valueOf(1_000))
        withSuccessfulPgasClaim()
        withSuccessfulSubmission()

        assertSuccess(prepareClaim())
        verifyPgasClaimed()
        verifyClearingSubmitted()
    }

    @Test
    fun `claims PGAS when the dry-run cannot fund the storage deposit`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost(dryRuns = listOf(UNFUNDED_DRY_RUN, FUNDED_DRY_RUN))
        withPgasBalances(BigInteger.valueOf(1_000))
        withSuccessfulPgasClaim()
        withSuccessfulSubmission()

        assertSuccess(prepareClaim())
        verifyPgasClaimed()
        verifyClearingSubmitted()
    }

    @Test
    fun `does not submit when the dry-run after a claim still cannot fund the deposit`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost(dryRuns = listOf(UNFUNDED_DRY_RUN))
        withPgasBalances(BigInteger.valueOf(1_000))
        withSuccessfulPgasClaim()

        val result = prepareClaim()

        assertTrue("expected DotNsQueueClearPgasShortError but was ${result.exceptionOrNull()}", result.exceptionOrNull() is DotNsQueueClearPgasShortError)
        verifyNothingSubmitted()
    }

    // A claim spends one of the day's slots, and funds fix only a deposit the account cannot pay.
    @Test
    fun `does not claim PGAS when the dry-run fails for another reason`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost(dryRuns = listOf(Result.failure(ReviveContractReverted(DataByteArray.empty()))))
        withPgasBalances(BigInteger.ZERO)

        assertTrue(prepareClaim().exceptionOrNull() is ReviveContractReverted)
        verifyNoPgasClaimed()
        verifyNothingSubmitted()
    }

    @Test
    fun `counts the storage deposit in the PGAS the clearing needs`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost(dryRuns = listOf(dryRun(deposit = BigInteger.valueOf(1_000))))
        withPgasBalances(BigInteger.valueOf(1_000), BigInteger.valueOf(1_000))
        withSuccessfulPgasClaim()

        val result = prepareClaim()

        assertTrue("expected DotNsQueueClearPgasShortError but was ${result.exceptionOrNull()}", result.exceptionOrNull() is DotNsQueueClearPgasShortError)
        verifyPgasClaimed()
        verifyNothingSubmitted()
    }

    @Test
    fun `tops up PGAS before clearing when the balance is short`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost()
        withPgasBalances(BigInteger.ZERO, BigInteger.valueOf(1_000))
        withSuccessfulPgasClaim()
        withSuccessfulSubmission()

        assertSuccess(prepareClaim())
        verifyPgasClaimed()
        verifyClearingSubmitted()
    }

    @Test
    fun `does not submit when PGAS stays short after a top-up`() = runBlocking<Unit> {
        withQueue(holder = OUR_HOLDER, deadInFront = 5)
        withMaxQueueStepsPerCall(BigInteger.valueOf(4))
        withWalletAccount(OUR_ACCOUNT_ID)
        withClearingCost()
        withPgasBalances(BigInteger.ZERO, BigInteger.ONE)
        withSuccessfulPgasClaim()

        val result = prepareClaim()

        assertTrue("expected DotNsQueueClearPgasShortError but was ${result.exceptionOrNull()}", result.exceptionOrNull() is DotNsQueueClearPgasShortError)
        verifyNothingSubmitted()
    }

    private suspend fun prepareClaim(): Result<Unit> = clearer.prepareClaim(chain, OUR_ACCOUNT_ID, LABEL, CONFIG)

    private fun withQueue(holder: ByteArray?, deadInFront: Int) = runBlocking {
        whenever(queueReader.read(eq(LABEL), eq(CONFIG)))
            .thenReturn(Result.success(DotNsReservationQueue(firstActiveHolder = holder, deadInFront = deadInFront)))
    }

    private fun withMaxQueueStepsPerCall(maxSteps: BigInteger?) = runBlocking {
        whenever(queueReader.maxQueueStepsPerCall(eq(CONFIG))).thenReturn(Result.success(maxSteps))
    }

    private fun withWalletAccount(accountId: AccountId) = runBlocking {
        val wallet: MetaAccount = mock()
        whenever(wallet.accountIdIn(chain)).thenReturn(accountId)
        whenever(accountRepository.getWalletAccount()).thenReturn(wallet)
    }

    private fun withClearingCost(
        mapped: List<Boolean> = listOf(true),
        dryRuns: List<Result<ReviveDryRun>> = listOf(FUNDED_DRY_RUN)
    ) = runBlocking {
        whenever(chain.id).thenReturn(CHAIN_ID)
        val mappedResults = mapped.map { Result.success(it) }
        whenever(reviveContractApi.isAccountMapped(CHAIN_ID, OUR_ACCOUNT_ID)).thenReturn(mappedResults.first(), *mappedResults.drop(1).toTypedArray())
        whenever(reviveContractApi.dryRun(eq(CHAIN_ID), eq(OUR_ACCOUNT_ID), eq(CONTROLLER), any())).thenReturn(dryRuns.first(), *dryRuns.drop(1).toTypedArray())
        val fee: AccountFee = mock()
        whenever(fee.amount).thenReturn(BigInteger.valueOf(500).intoBalance())
        whenever(extrinsicService.estimateFee(any(), any(), any(), any())).thenReturn(Result.success(fee))
    }

    private fun withPgasBalances(first: BigInteger, vararg next: BigInteger) = runBlocking {
        val asset: Chain.Asset = mock()
        whenever(pgasChainAssetProvider.asset()).thenReturn(asset)
        whenever(tokenBalanceTypeRegistry.typeFor(asset)).thenReturn(pgasBalanceType)
        val balances = (listOf(first) + next).map { amount ->
            mock<TokenBalance>().also { balance -> whenever(balance.transferable).thenReturn(amount.intoBalance()) }
        }
        whenever(pgasBalanceType.getBalance(OUR_ACCOUNT_ID)).thenReturn(balances.first(), *balances.drop(1).toTypedArray())
    }

    private fun withSuccessfulPgasClaim() = runBlocking {
        val collection = PeopleCollection.entries.first()
        whenever(activePeopleCollectionUseCase.getActivePeopleCollection()).thenReturn(collection)
        whenever(peopleCheckMemberInRingUseCase.awaitIncluded(collection)).thenReturn(Result.success(Unit))
        with(StalenessReportCollector.NoOp) {
            whenever(pgasClaimer.claim(OUR_ACCOUNT_ID, OnExistingAllocationStrategy.INCREASE)).thenReturn(Result.success(Unit))
        }
    }

    private fun withSuccessfulSubmission() = runBlocking {
        whenever(extrinsicService.submitExtrinsicAndAwaitExecution(any(), any(), any(), any(), any())).thenReturn(
            Result.success(
                ExtrinsicExecutionResult(
                    extrinsicHash = "0xext",
                    blockHash = "0xblk",
                    outcome = ExtrinsicDispatch.Ok(emittedEvents = emptyList())
                )
            )
        )
    }

    private fun verifyNothingSubmitted() = runBlocking<Unit> {
        verify(extrinsicService, never()).submitExtrinsicAndAwaitExecution(any(), any(), any(), any(), any())
    }

    private fun verifyNotDryRun() = runBlocking<Unit> {
        verify(reviveContractApi, never()).dryRun(any(), any(), any(), any())
    }

    private fun verifyClearingSubmitted() = runBlocking<Unit> {
        verify(extrinsicService).submitExtrinsicAndAwaitExecution(any(), any(), any(), any(), any())
    }

    private fun verifyNoPgasClaimed() = runBlocking<Unit> {
        with(StalenessReportCollector.NoOp) {
            verify(pgasClaimer, never()).claim(OUR_ACCOUNT_ID, OnExistingAllocationStrategy.INCREASE)
        }
    }

    private fun verifyPgasClaimed() = runBlocking<Unit> {
        with(StalenessReportCollector.NoOp) {
            verify(pgasClaimer).claim(OUR_ACCOUNT_ID, OnExistingAllocationStrategy.INCREASE)
        }
    }

    private fun assertSuccess(result: Result<*>) {
        assertTrue("expected Result.success but was ${result.exceptionOrNull()}", result.isSuccess)
    }

    private companion object {
        const val CHAIN_ID = "asset-hub-chain-id"
        const val LABEL = "byteboro"
        val OUR_ACCOUNT_ID: AccountId = ByteArray(32) { 1 }.toDataByteArray()
        val OUR_HOLDER: ByteArray = OUR_ACCOUNT_ID.toEvmAccountId().value
        val OTHER_HOLDER: ByteArray = ByteArray(20) { 9 }
        val CONTROLLER: DataByteArray = ByteArray(20) { 4 }.toDataByteArray()
        val CONFIG = DotNsGatewayConfig(popControllerAddress = CONTROLLER, popResolverAddress = ByteArray(20) { 5 }.toDataByteArray())
        val WEIGHT = WeightV2(refTime = BigInteger.valueOf(100), proofSize = BigInteger.valueOf(10))
        val FUNDED_DRY_RUN: Result<ReviveDryRun> = dryRun(deposit = BigInteger.ZERO)
        val UNFUNDED_DRY_RUN: Result<ReviveDryRun> = Result.failure(ReviveStorageDepositUnfunded(WEIGHT, BigInteger.valueOf(100)))

        fun dryRun(deposit: BigInteger): Result<ReviveDryRun> =
            Result.success(ReviveDryRun(data = DataByteArray.empty(), weightRequired = WEIGHT, storageDeposit = deposit))
    }
}
