package io.paritytech.polkadotapp.feature_coinage_impl.presentation.mixin

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_coinage_impl.domain.installation.CoinageInstallationGate
import io.paritytech.polkadotapp.test_shared.awaitCompletes
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.time.Duration.Companion.milliseconds

class RealCoinageSetupNoticeMixinTest {
    private val hold = MutableStateFlow(false)
    private val gate: CoinageInstallationGate = mock(CoinageInstallationGate::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mixin by lazy { RealCoinageSetupNoticeMixin(ComputationalScope(scope), gate) }

    @Before
    fun setUp() {
        whenever(gate.subscribeOnHold()).thenReturn(hold)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `nothing on hold shows no notice`() = runBlocking<Unit> {
        withHold(false)
        val notice = mixin.isVisible

        settle()

        assertFalse(notice.value)
    }

    @Test
    fun `a hold shows the notice`() = runBlocking<Unit> {
        withHold(true)

        awaitVisible()
    }

    @Test
    fun `dismissing hides the notice while the hold stays`() = runBlocking<Unit> {
        withHold(true)
        awaitVisible()

        mixin.dismiss()

        awaitHidden()
        settle()
        assertFalse(mixin.isVisible.value)
    }

    @Test
    fun `the hold clearing hides the notice without a dismissal`() = runBlocking<Unit> {
        withHold(true)
        awaitVisible()

        withHold(false)

        awaitHidden()
    }

    @Test
    fun `a new hold after a dismissal shows the notice again`() = runBlocking<Unit> {
        withHold(true)
        awaitVisible()
        mixin.dismiss()
        awaitHidden()

        withHold(false)
        settle()
        withHold(true)

        awaitVisible()
    }

    @Test
    fun `a dismissal while nothing is on hold does not pre-dismiss the next hold`() = runBlocking<Unit> {
        withHold(true)
        awaitVisible()
        mixin.dismiss()
        awaitHidden()
        withHold(false)
        settle()

        mixin.dismiss()
        settle()
        withHold(true)

        awaitVisible()
    }

    private fun withHold(onHold: Boolean) {
        hold.value = onHold
    }

    private suspend fun awaitVisible() = awaitCompletes { mixin.isVisible.first { it } }

    private suspend fun awaitHidden() = awaitCompletes { mixin.isVisible.first { !it } }

    // A step that changes nothing visible cannot be awaited; the next input must not be conflated with it.
    private suspend fun settle() = delay(EVENT_SETTLE)

    private companion object {
        val EVENT_SETTLE = 250.milliseconds
    }
}
