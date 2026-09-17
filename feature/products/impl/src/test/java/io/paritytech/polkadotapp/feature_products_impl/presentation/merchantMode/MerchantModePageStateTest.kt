package io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantModePageStateTest {
    private val loading = MerchantModePageState.Loading(DotNsLoadProgress.Resolving)

    @Test
    fun `a served archive shows the terminal`() {
        assertEquals(
            MerchantModePageState.Content,
            loading.next(DotNsLoadProgress.Completed)
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
            loading.next(DotNsLoadProgress.Failed(IllegalStateException("no content")))
        )
    }

    @Test
    fun `an archive still in flight keeps the screen loading with its progress`() {
        assertEquals(
            MerchantModePageState.Loading(DotNsLoadProgress.Downloading(0.5f)),
            loading.next(DotNsLoadProgress.Downloading(0.5f))
        )
    }
}
