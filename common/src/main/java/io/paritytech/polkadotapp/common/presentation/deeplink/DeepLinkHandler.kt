package io.paritytech.polkadotapp.common.presentation.deeplink

import android.net.Uri
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import timber.log.Timber

interface DeepLinkHandler {
    fun canHandle(data: Uri): Boolean

    context(ComputationalScope)
    suspend fun handle(data: Uri): Result<DeeplinkProcessingOutcome>

    companion object {
        const val APP_SCHEME = "polkadotapp"
        const val WEB_HTTP_SCHEME = "http"
        const val WEB_HTTPS_SCHEME = "https"
    }
}

sealed class DeeplinkProcessingOutcome {
    class ShowMessage(val message: String) : DeeplinkProcessingOutcome()

    class Navigate(val navigate: () -> Unit) : DeeplinkProcessingOutcome()

    data object NoOp : DeeplinkProcessingOutcome()
}

fun Result<DeeplinkProcessingOutcome>.flatten(): DeeplinkProcessingOutcome {
    return fold(
        onSuccess = { it },
        onFailure = {
            it.message?.let(DeeplinkProcessingOutcome::ShowMessage)
                ?: DeeplinkProcessingOutcome.NoOp
        }
    )
}

suspend fun DeepLinkHandler.tryCanHandle(data: Uri): Boolean {
    return runCatching { canHandle(data) }
        .onFailure { Timber.e("Deeplink canHandle threw an error: $it") }
        .getOrDefault(false)
}
