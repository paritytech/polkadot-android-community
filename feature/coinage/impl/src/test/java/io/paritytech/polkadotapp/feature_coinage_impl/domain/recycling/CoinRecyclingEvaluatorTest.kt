package io.paritytech.polkadotapp.feature_coinage_impl.domain.recycling

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinProvenance
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinRecyclingState
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageAccountBackupStatus
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageAccountBackupStatus.Completed
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageAccountBackupStatus.Delayed
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageAccountBackupStatus.Registering
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclingVerdicts
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinageRecyclingStrategySettings
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageAccountBackupObserver
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageAssetState
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageAssetsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageBalanceConverterUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageRecyclingUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedVoucher
import io.paritytech.polkadotapp.feature_coinage_impl.common.testConversionContext
import io.paritytech.polkadotapp.feature_coinage_impl.data.source.ClockChangesSource
import io.paritytech.polkadotapp.feature_coinage_impl.testKey
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.testDispatchers
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CoinRecyclingEvaluatorTest {
    @Test
    fun `verdicts are published before the backup status has emitted`() = runTest {
        val fixture = EvaluatorFixture(this, status = MutableSharedFlow())

        fixture.start()

        assertEquals(CoinRecyclingState.MUST_RECYCLE, fixture.verdicts.last().getValue(DUE_COIN.derivationIndex))
        assertEquals(emptyList<List<Coin>>(), fixture.recycled)
    }

    @Test
    fun `while the backup is pending verdicts are published and nothing is recycled`() = runTest {
        val status = MutableStateFlow<CoinageAccountBackupStatus>(Registering)
        val fixture = EvaluatorFixture(this, status)

        fixture.start()
        status.value = Delayed
        runCurrent()

        assertEquals(CoinRecyclingState.MUST_RECYCLE, fixture.verdicts.last().getValue(DUE_COIN.derivationIndex))
        assertEquals(emptyList<List<Coin>>(), fixture.recycled)
    }

    @Test
    fun `a completed backup recycles the gated coins without a new asset event, once`() = runTest {
        val status = MutableSharedFlow<CoinageAccountBackupStatus>(replay = 1)
        status.emit(Registering)
        val fixture = EvaluatorFixture(this, status)

        fixture.start()
        status.emit(Completed)
        runCurrent()
        status.emit(Completed)
        runCurrent()

        assertEquals(listOf(listOf(DUE_COIN)), fixture.recycled)
    }

    private class EvaluatorFixture(private val scope: TestScope, status: Flow<CoinageAccountBackupStatus>) {
        val recycled = mutableListOf<List<Coin>>()
        val verdicts = mutableListOf<RecyclingVerdicts>()

        private val clock = object : TimeProvider {
            override fun now() = Instant.fromEpochMilliseconds(scope.testScheduler.currentTime)
        }
        private val updates = VoucherReadinessUpdates(clock, mock<ClockChangesSource>().also { whenever(it.changes()).thenReturn(emptyFlow()) })
        private val assets: CoinageAssetsUseCase = mock()
        private val settings: CoinageRecyclingStrategySettings = mock()
        private val capacities: RingCapacityProvider = mock()
        private val converter: CoinageBalanceConverterUseCase = mock()
        private val quota: UnloadQuotaTracker = mock()
        private val recycling: CoinageRecyclingUseCase = mock()
        private val backupObserver: CoinageAccountBackupObserver = mock()

        init {
            runBlocking {
                whenever(assets.subscribeCoins()).thenReturn(MutableStateFlow(listOf(TrackedCoin(DUE_COIN, CoinageAssetState.UNTRACKED))))
                whenever(assets.subscribeVouchers()).thenReturn(MutableStateFlow(emptyList<TrackedVoucher>()))
                whenever(settings.strategyFlow()).thenReturn(MutableStateFlow(RecyclingStrategyType.MIN_PRIVACY))
                whenever(capacities.peekCapacitiesFor(any())).thenReturn(emptyMap())
                whenever(capacities.capacitiesFor(any())).thenReturn(Result.success(emptyMap()))
                whenever(converter.create()).thenReturn(Result.success(testConversionContext))
                whenever(quota.isQuotaRunningLow()).thenReturn(Result.success(false))
                whenever(backupObserver.subscribeStatus()).thenReturn(status)
                whenever(recycling.recycle(any())).thenAnswer { invocation ->
                    recycled += invocation.getArgument<List<Coin>>(0)
                    Result.success(Unit)
                }
            }
        }

        private val evaluator = CoinRecyclingEvaluator(
            assets,
            settings,
            RecyclingStrategyProvider(forcedAgeOf(FORCED_AGE), quota),
            VoucherUsabilityContextFactory(capacities, clock),
            converter,
            recycling,
            scope.testDispatchers(),
            updates,
            backupObserver,
        )

        fun start() {
            scope.backgroundScope.launch { evaluator.verdicts.collect { verdicts += it } }
            with(ComputationalScope(scope.backgroundScope)) { evaluator.start() }
            scope.runCurrent()
        }
    }

    private companion object {
        const val FORCED_AGE = 14

        val DUE_COIN = Coin(testKey(0), ValueExponent(1), Coin.Age.Known(FORCED_AGE), true, mock(), CoinProvenance.UNKNOWN)
    }
}
