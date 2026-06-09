package io.paritytech.polkadotapp.feature_upgrade_username_impl.domain.usecase

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.domain.model.MetaAccount
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.ResourcesRepository
import io.paritytech.polkadotapp.feature_chain_resources_api.domain.model.UsernameReservation
import io.paritytech.polkadotapp.feature_upgrade_username_api.domain.model.UpgradeUsernameAvailabilityState
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.eq
import io.paritytech.polkadotapp.test_shared.whenever
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.time.Duration.Companion.hours

class RealCheckUsernameAvailabilityUseCaseTest {
    private val knownChains = KnownChains(people = CHAIN_ID, assetHub = "", bulletIn = "", hydration = null)
    private val chainRegistry: ChainRegistry = mock()
    private val accountRepository: AccountRepository = mock()
    private val resourcesRepository: ResourcesRepository = mock()

    private val useCase = RealCheckUsernameAvailabilityUseCase(
        knownChains = knownChains,
        chainRegistry = chainRegistry,
        accountRepository = accountRepository,
        resourcesRepository = resourcesRepository,
    )

    @Test
    fun `Free when unowned and the reservation queue is empty`() = runBlocking<Unit> {
        stubOurAccount()
        stubOwner(null)
        whenever(resourcesRepository.usernameReservationQueue(eq(CHAIN_ID), eq(NAME)))
            .thenReturn(Result.success(emptyList()))

        assertEquals(UpgradeUsernameAvailabilityState.Free, useCase(NAME).getOrThrow())
    }

    @Test
    fun `ReservedByUs when our reservation heads the queue`() = runBlocking<Unit> {
        stubOurAccount()
        stubOwner(null)
        whenever(resourcesRepository.usernameReservationQueue(eq(CHAIN_ID), eq(NAME)))
            .thenReturn(Result.success(listOf(UsernameReservation(account = OUR_ACCOUNT_ID, joinedAt = 0L))))

        assertEquals(UpgradeUsernameAvailabilityState.ReservedByUs, useCase(NAME).getOrThrow())
    }

    @Test
    fun `NotAvailable when someone else holds a live reservation`() = runBlocking<Unit> {
        stubOurAccount()
        stubOwner(null)
        whenever(resourcesRepository.usernameReservationQueue(eq(CHAIN_ID), eq(NAME)))
            .thenReturn(Result.success(listOf(UsernameReservation(OTHER_ACCOUNT_ID, joinedAt = System.currentTimeMillis()))))
        whenever(resourcesRepository.reservationDuration(eq(CHAIN_ID))).thenReturn(Result.success(1.hours))

        assertEquals(UpgradeUsernameAvailabilityState.NotAvailable, useCase(NAME).getOrThrow())
    }

    @Test
    fun `ReclaimExpiredReservation when reservations ahead of us have expired`() = runBlocking<Unit> {
        stubOurAccount()
        stubOwner(null)
        whenever(resourcesRepository.usernameReservationQueue(eq(CHAIN_ID), eq(NAME)))
            .thenReturn(Result.success(listOf(UsernameReservation(OTHER_ACCOUNT_ID, joinedAt = 0L))))
        whenever(resourcesRepository.reservationDuration(eq(CHAIN_ID))).thenReturn(Result.success(1.hours))

        val state = useCase(NAME).getOrThrow()

        assertTrue(state is UpgradeUsernameAvailabilityState.ReclaimExpiredReservation)
        assertEquals(listOf(OTHER_ACCOUNT_ID), (state as UpgradeUsernameAvailabilityState.ReclaimExpiredReservation).expiredAccounts)
    }

    @Test
    fun `NotAvailable when the name is already owned`() = runBlocking<Unit> {
        stubOurAccount()
        stubOwner(byteArrayOf(7).toDataByteArray())

        assertEquals(UpgradeUsernameAvailabilityState.NotAvailable, useCase(NAME).getOrThrow())
    }

    private fun stubOwner(owner: AccountId?) = runBlocking {
        whenever(resourcesRepository.accountIdOfUsername(eq(CHAIN_ID), eq(NAME))).thenReturn(Result.success(owner))
    }

    private fun stubOurAccount() = runBlocking {
        val chain = mock<Chain>()
        whenever(chainRegistry.getChain(eq(CHAIN_ID))).thenReturn(chain)
        val account = mock<MetaAccount>().also { whenever(it.accountIdIn(any())).thenReturn(OUR_ACCOUNT_ID) }
        whenever(accountRepository.getWalletAccount()).thenReturn(account)
    }

    private companion object {
        const val CHAIN_ID = "people-chain-id"
        const val NAME = "byteboro"
        val OUR_ACCOUNT_ID: AccountId = ByteArray(32) { 1 }.toDataByteArray()
        val OTHER_ACCOUNT_ID: AccountId = ByteArray(32) { 9 }.toDataByteArray()
    }
}
