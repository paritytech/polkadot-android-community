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
        seed.offer(990, 618)

        verifyCapturedOnly(990, 618)
    }

    @Test
    fun `does not capture when a frame was already captured`() {
        seed.offer(990, 618)
        seed.offer(1080, 674)

        verifyCapturedOnly(990, 618)
    }

    @Test
    fun `captures the next frame when the first one had no size`() {
        seed.offer(0, 0)
        seed.offer(990, 618)

        verifyCapturedOnly(990, 618)
    }

    @Test
    fun `captures once when two surfaces offer at the same time`() {
        val firstCapturing = CountDownLatch(1)
        val secondOffered = CountDownLatch(1)
        withCaptureHeldUntil(secondOffered, signalling = firstCapturing)

        val first = thread { seed.offer(990, 618) }
        assertTrue(firstCapturing.await(WAIT_SECONDS, TimeUnit.SECONDS))
        seed.offer(1080, 674)
        secondOffered.countDown()
        first.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))

        verifyCapturedOnly(990, 618)
    }

    private fun withCaptureHeldUntil(release: CountDownLatch, signalling: CountDownLatch) {
        whenever(capture(anyInt(), anyInt())).thenAnswer {
            signalling.countDown()
            release.await(WAIT_SECONDS, TimeUnit.SECONDS)
            Unit
        }
    }

    private fun verifyCapturedOnly(width: Int, height: Int) {
        verify(capture).invoke(width, height)
        verifyNoMoreInteractions(capture)
    }

    private companion object {
        const val WAIT_SECONDS = 2L
    }
}
