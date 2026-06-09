package io.paritytech.polkadotapp.common.presentation.deeplink

import android.net.Uri
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RootDeeplinkHandler @Inject constructor(
    private val coroutineDispatchers: CoroutineDispatchers,
    private val handlers: Set<@JvmSuppressWildcards DeepLinkHandler>,
) : DeepLinkHandler {
    override fun canHandle(data: Uri): Boolean {
        return handlers.any { it.canHandle(data) }
    }

    context(ComputationalScope)
    override suspend fun handle(data: Uri): Result<DeeplinkProcessingOutcome> = withContext(coroutineDispatchers.computation) {
        handlers.find { it.tryCanHandle(data) }
            ?.handle(data)
            ?: Result.failure(IllegalArgumentException("No matching handlers found to handle deeplink: $data"))
    }
}
