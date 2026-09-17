package io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantModePageStateTest {
    @Test
    fun `a served archive shows the terminal`() {
        assertEquals(
            MerchantModePageState.Content,
            MerchantModePageState.Loading.next(DotNsLoadProgress.Completed)
        )
    }

    @Test
    fun `the terminal keeps the screen once it is served`() {
        val progresses = listOf(
            DotNsLoadProgress.Resolving,
            DotNsLoadProgress.Downloading(0.2f),
            DotNsLoadProgress.Unpacking,
            DotNsLoadProgress.Failed(IllegalStateException("a navigation the terminal made")),
        )

        progresses.forEach { progress ->
            assertEquals(
                "$progress must not blank the terminal out",
                MerchantModePageState.Content,
                MerchantModePageState.Content.next(progress)
            )
        }
    }

    @Test
    fun `a failure before the first archive leaves the terminal unavailable`() {
        assertEquals(
            MerchantModePageState.Unavailable,
            MerchantModePageState.Loading.next(DotNsLoadProgress.Failed(IllegalStateException("no content")))
        )
    }

    @Test
    fun `an archive still in flight keeps the screen loading`() {
        assertEquals(
            MerchantModePageState.Loading,
            MerchantModePageState.Loading.next(DotNsLoadProgress.Downloading(0.5f))
        )
    }
}
