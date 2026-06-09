package io.paritytech.polkadotapp.feature_products_impl.domain.product

import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Resolves worker script by extracting `/worker/index.js` from the DotNs content archive.
 */
class ArchiveScriptResolver @Inject constructor(
    private val dotNsResolver: DotNsResolver,
) : ProductScriptResolver {
    override suspend fun resolveScript(productId: ProductId): Result<String> = runCatching {
        val contentUri = dotNsResolver.resolveToLocalUri(productId.value).getOrThrow()
        val contentDir = File(contentUri.path!!)
        val workerScript = File(contentDir, WORKER_SCRIPT_PATH)

        require(workerScript.exists()) {
            "Worker script not found at $WORKER_SCRIPT_PATH for product ${productId.value}"
        }

        withContext(Dispatchers.IO) {
            workerScript.readText()
        }
    }

    override suspend fun canResolveScript(productId: ProductId): Boolean {
        val contentUri = dotNsResolver.resolveToLocalUri(productId.value).getOrNull() ?: return false
        val workerScript = File(File(contentUri.path!!), WORKER_SCRIPT_PATH)
        return workerScript.exists()
    }

    companion object {
        private const val WORKER_SCRIPT_PATH = "worker/index.js"
    }
}
