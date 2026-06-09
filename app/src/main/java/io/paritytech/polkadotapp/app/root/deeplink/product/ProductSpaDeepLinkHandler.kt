package io.paritytech.polkadotapp.app.root.deeplink.product

import android.net.Uri
import io.paritytech.polkadotapp.app.root.presentation.root.RootRouter
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.deeplink.DeepLinkHandler
import io.paritytech.polkadotapp.common.presentation.deeplink.DeepLinkHandler.Companion.WEB_HTTPS_SCHEME
import io.paritytech.polkadotapp.common.presentation.deeplink.DeeplinkProcessingOutcome
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.awaitAccountsInitialized
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsUtils
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class ProductSpaDeepLinkHandler @Inject constructor(
    private val coroutineDispatchers: CoroutineDispatchers,
    private val accountRepository: AccountRepository,
    private val rootRouter: RootRouter,
) : DeepLinkHandler {
    override fun canHandle(data: Uri): Boolean = DotNsUtils.isDotDomain(data)

    context(ComputationalScope)
    override suspend fun handle(data: Uri): Result<DeeplinkProcessingOutcome> =
        withContext(coroutineDispatchers.io) {
            runCancellableCatching {
                accountRepository.awaitAccountsInitialized()

                val httpsUri = data.buildUpon().scheme(WEB_HTTPS_SCHEME).build()
                val normalized = DotNsUtils.normalize(httpsUri)
                    ?: error("Not a .dot domain: $data")

                DeeplinkProcessingOutcome.Navigate {
                    rootRouter.openSpaBrowser(normalized.toString())
                }
            }
        }
}
