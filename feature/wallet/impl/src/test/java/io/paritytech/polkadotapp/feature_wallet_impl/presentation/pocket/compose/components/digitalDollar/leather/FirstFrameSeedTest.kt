package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.leather

import io.paritytech.polkadotapp.test_shared.anyInt
import io.paritytech.polkadotapp.test_shared.whenever
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FirstFrameSeedTest {
    @Suppress("UNCHECKED_CAST")
    private val capture = mock(Function2::class.java) as (Int, Int) -> Unit

    private val seed = FirstFrameSeed(capture)

    @Test
    fun `captures the frame when it is the first sized one`() {
        seed.offer(WIDTH, HEIGHT)

        verifyCapturedOnly(WIDTH, HEIGHT)
    }

    @Test
    fun `does not capture when a frame was already captured`() {
        seed.offer(WIDTH, HEIGHT)
        seed.offer(OTHER_WIDTH, OTHER_HEIGHT)

        verifyCapturedOnly(WIDTH, HEIGHT)
    }

    @Test
    fun `captures the next frame when the first one had no width`() {
        seed.offer(0, HEIGHT)
        seed.offer(WIDTH, HEIGHT)

        verifyCapturedOnly(WIDTH, HEIGHT)
    }

    @Test
    fun `captures the next frame when the first one had no height`() {
        seed.offer(WIDTH, 0)
        seed.offer(WIDTH, HEIGHT)

        verifyCapturedOnly(WIDTH, HEIGHT)
    }

    @Test
    fun `captures once when two surfaces offer at the same time`() {
        offerOtherWhileFirstCaptureIsHeld()

        verifyCapturedOnly(WIDTH, HEIGHT)
    }

    private fun offerOtherWhileFirstCaptureIsHeld() {
        val firstCapturing = CountDownLatch(1)
        val otherOffered = CountDownLatch(1)
        whenever(capture(anyInt(), anyInt())).thenAnswer {
            firstCapturing.countDown()
            otherOffered.await(WAIT_SECONDS, TimeUnit.SECONDS)
            Unit
        }

        val first = thread { seed.offer(WIDTH, HEIGHT) }
        assertTrue("the first offer never reached capture", firstCapturing.await(WAIT_SECONDS, TimeUnit.SECONDS))
        seed.offer(OTHER_WIDTH, OTHER_HEIGHT)
        otherOffered.countDown()
        first.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
    }

    private fun verifyCapturedOnly(width: Int, height: Int) {
        verify(capture).invoke(width, height)
        verifyNoMoreInteractions(capture)
    }

    private companion object {
        const val WIDTH = 990
        const val HEIGHT = 618
        const val OTHER_WIDTH = 1080
        const val OTHER_HEIGHT = 674
        const val WAIT_SECONDS = 2L
    }
}
