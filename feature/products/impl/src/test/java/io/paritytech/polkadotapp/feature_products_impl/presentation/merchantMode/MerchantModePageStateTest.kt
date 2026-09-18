package io.paritytech.polkadotapp.feature_products_impl.presentation.merchantMode

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import io.paritytech.polkadotapp.feature_products_api.domain.error.ProductResolutionError
import io.paritytech.polkadotapp.feature_products_api.model.ExecutableHost
import io.paritytech.polkadotapp.feature_products_api.model.Executables
import io.paritytech.polkadotapp.feature_products_api.model.Product
import io.paritytech.polkadotapp.feature_products_api.model.ProductExecutable
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.ResolvedProduct
import io.paritytech.polkadotapp.feature_products_api.model.SemVer
import io.paritytech.polkadotapp.feature_products_impl.presentation.productLoad.PageLoad
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantModePageStateTest {
    private val loading: MerchantModePageState = MerchantModePageState.Loading(DotNsLoadProgress.Resolving)

    @Test
    fun `a served archive shows the terminal`() {
        assertEquals(
            MerchantModePageState.Content,
            loading.next(serving(DotNsLoadProgress.Completed))
        )
    }

    @Test
    fun `the terminal keeps the screen once it is served`() {
        val laterLoads = listOf(
            PageLoad.Resolving,
            serving(DotNsLoadProgress.Downloading(0.2f)),
            serving(DotNsLoadProgress.Unpacking),
            serving(DotNsLoadProgress.Failed(IllegalStateException("a navigation the terminal made"))),
            PageLoad.Failed(ProductResolutionError.Unknown),
        )

        laterLoads.forEach { load ->
            assertEquals(
                "$load must not blank the terminal out",
                MerchantModePageState.Content,
                MerchantModePageState.Content.next(load)
            )
        }
    }

    @Test
    fun `a failure before the first archive leaves the terminal unavailable`() {
        assertEquals(
            MerchantModePageState.Unavailable,
            loading.next(PageLoad.Failed(ProductResolutionError.Unknown))
        )
    }

    @Test
    fun `a terminal that publishes no app surface is unavailable`() {
        assertEquals(
            MerchantModePageState.Unavailable,
            loading.next(serving(DotNsLoadProgress.Completed, hasApp = false))
        )
    }

    @Test
    fun `an archive that fails to load leaves the terminal unavailable`() {
        assertEquals(
            MerchantModePageState.Unavailable,
            loading.next(serving(DotNsLoadProgress.Failed(IllegalStateException("gateway is down"))))
        )
    }

    @Test
    fun `an archive still in flight keeps the screen loading with its progress`() {
        assertEquals(
            MerchantModePageState.Loading(DotNsLoadProgress.Downloading(0.5f)),
            loading.next(serving(DotNsLoadProgress.Downloading(0.5f)))
        )
    }

    @Test
    fun `the address itself still resolving keeps the screen loading`() {
        assertEquals(
            MerchantModePageState.Loading(DotNsLoadProgress.Resolving),
            loading.next(PageLoad.Resolving)
        )
    }

    private fun serving(progress: DotNsLoadProgress, hasApp: Boolean = true): PageLoad.Serving {
        val id = ProductId.fromStoredValue(MERCHANT_DOMAIN)

        return PageLoad.Serving(
            resolved = ResolvedProduct(
                product = Product(id = id, name = "T3rminal", icon = null),
                executables = Executables(
                    app = if (hasApp) ProductExecutable.App(ExecutableHost(MERCHANT_DOMAIN), SemVer.ZERO) else null,
                    widget = null,
                    worker = null,
                ),
            ),
            progress = progress,
        )
    }
}

private const val MERCHANT_DOMAIN = "terminal.paseo"
