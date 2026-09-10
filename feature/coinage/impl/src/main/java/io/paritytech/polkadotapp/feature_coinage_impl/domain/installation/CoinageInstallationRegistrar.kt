package io.paritytech.polkadotapp.feature_coinage_impl.domain.installation

import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.connection.ChainConnectionRefCounter
import io.paritytech.polkadotapp.chains.multiNetwork.connection.withConnectionEnabled
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageAccountBackupStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageAccountBackupObserver
import io.paritytech.polkadotapp.feature_coinage_impl.data.dataStore.AccountDataStoreConfigProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.installation.CoinageInstallationRepository
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTransactionService
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxState
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// The durable engine decides what each attempt did; this only decides when another attempt is needed. It never
// submits while one is live: the oracle reads the same record for every attempt, so two in flight would both be
// credited with it. Every decision reads the ledger itself — the group flow only wakes the loop, because a Room
// emission can be older than a commit that already happened.
@Singleton
class CoinageInstallationRegistrar @Inject constructor(
    private val installationRepository: CoinageInstallationRepository,
    private val configProvider: AccountDataStoreConfigProvider,
    private val durableTransactionService: DurableTransactionService,
    private val submitter: InstallationRegistrationSubmitter,
    private val chainConnectionRefCounter: ChainConnectionRefCounter,
    private val knownChains: KnownChains,
) : CoinageAccountBackupObserver {
    private val runMutex = Mutex()
    private val resolvedTarget = MutableStateFlow<InstallationRegistrationTarget?>(null)
    private val reverted = MutableStateFlow(false)
    private val delayed = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun subscribeStatus(): Flow<CoinageAccountBackupStatus> {
        val completed = registrationTargets().flatMapLatest { target ->
            if (target == null) flowOf(false) else groupStates(target).map { states -> states.isFinalized() }
        }

        return combine(completed, reverted, delayed) { isCompleted, isReverted, isDelayed ->
            when {
                isCompleted -> CoinageAccountBackupStatus.Completed
                isReverted -> CoinageAccountBackupStatus.Reverted
                isDelayed -> CoinageAccountBackupStatus.Delayed
                else -> CoinageAccountBackupStatus.Registering
            }
        }
    }

    suspend fun register() {
        runMutex.withLock {
            reverted.value = false
            delayed.value = false

            durableTransactionService.startRecovery()

            chainConnectionRefCounter.withConnectionEnabled(knownChains.assetHub, CONNECTION_LABEL) {
                coroutineScope {
                    val delayTimer = launch {
                        delay(EXPECTED_REGISTRATION_TIME)
                        delayed.value = true
                    }

                    val target = awaitTarget()
                    resolvedTarget.value = target
                    val revertTracker = launch { trackReverts(target) }

                    submitUntilFinalized(target)

                    delayTimer.cancel()
                    revertTracker.cancel()
                }
            }
        }
    }

    private fun registrationTargets(): Flow<InstallationRegistrationTarget?> = flow {
        emit(resolveTargetOrNull())
        emitAll(resolvedTarget.filterNotNull())
    }.distinctUntilChanged()

    private suspend fun awaitTarget(): InstallationRegistrationTarget {
        while (true) {
            resolveTargetOrNull()?.let { return it }
            delay(INITIAL_BACKOFF)
        }
    }

    private suspend fun resolveTargetOrNull(): InstallationRegistrationTarget? {
        val installation = installationRepository.getOrCreateCurrent()

        return configProvider.contractAddress()
            .logFailure("Data store contract address is not available")
            .map { contract -> InstallationRegistrationTarget(contract, installation) }
            .getOrNull()
    }

    private suspend fun submitUntilFinalized(target: InstallationRegistrationTarget) {
        var attemptsThisRun = 0
        var backoffPending = false

        while (true) {
            val states = currentStates(target) ?: continue

            when {
                states.isFinalized() -> return
                states.any { it.status.isLive } -> awaitChange(target, states)
                backoffPending -> {
                    delay(backoffFor(attemptsThisRun))
                    backoffPending = false
                }
                else -> {
                    attemptsThisRun++
                    backoffPending = true
                    submitter.submitAttempt(target).logFailure("Installation registration attempt could not be submitted")
                }
            }
        }
    }

    private suspend fun currentStates(target: InstallationRegistrationTarget): List<DurableTxState>? {
        return durableTransactionService.getGroupStates(COINAGE_INSTALLATION_DOMAIN, target.registrationGroup())
            .logFailure("Could not read installation registration attempts")
            .onFailure { delay(INITIAL_BACKOFF) }
            .getOrNull()
    }

    private suspend fun awaitChange(target: InstallationRegistrationTarget, seen: List<DurableTxState>) {
        groupStates(target).first { it != seen }
    }

    private suspend fun trackReverts(target: InstallationRegistrationTarget) {
        var previous = emptyMap<DurableTxId, DurableTxStatus>()

        groupStates(target).collect { states ->
            val wasDemoted = states.any { it.status == DurableTxStatus.PENDING && previous[it.id] == DurableTxStatus.PENDING_SUCCESS }
            val isIncludedAgain = states.any { it.status == DurableTxStatus.PENDING_SUCCESS }

            when {
                wasDemoted -> reverted.value = true
                isIncludedAgain -> reverted.value = false
            }

            previous = states.associate { it.id to it.status }
        }
    }

    private fun groupStates(target: InstallationRegistrationTarget): Flow<List<DurableTxState>> {
        return durableTransactionService.subscribeGroupStates(COINAGE_INSTALLATION_DOMAIN, target.registrationGroup())
            .distinctUntilChanged()
    }

    private fun List<DurableTxState>.isFinalized() = any { it.status == DurableTxStatus.FINALIZED_SUCCESS }

    private fun backoffFor(attempts: Int): Duration {
        val exponential = INITIAL_BACKOFF * (1 shl (attempts - 1).coerceAtMost(MAX_BACKOFF_DOUBLINGS))
        return exponential.coerceAtMost(MAX_BACKOFF)
    }

    private companion object {
        const val CONNECTION_LABEL = "CoinageInstallationRegistrar"
        const val MAX_BACKOFF_DOUBLINGS = 10

        val EXPECTED_REGISTRATION_TIME = 30.seconds
        val INITIAL_BACKOFF = 5.seconds
        val MAX_BACKOFF = 5.minutes
    }
}
