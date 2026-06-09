package io.paritytech.polkadotapp.feature_products_impl.domain.webView

import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.CallingProductIdProvider
import io.paritytech.polkadotapp.feature_products_impl.domain.hostApi.getProductIdOrNull
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.ProductPermissionGuard
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.DeviceCapabilityType
import io.paritytech.polkadotapp.feature_products_impl.domain.permissions.models.ProductPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class ProductWebChromeClient @AssistedInject constructor(
    private val permissionGuard: ProductPermissionGuard,
    @Assisted private val logPrefix: String,
    @Assisted private val callingProductIdProvider: CallingProductIdProvider,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val onTitleReceived: ((String) -> Unit)?,
) : WebChromeClient() {
    @AssistedFactory
    interface Factory {
        fun create(
            logPrefix: String,
            callingProductIdProvider: CallingProductIdProvider,
            scope: CoroutineScope,
            onTitleReceived: ((String) -> Unit)?,
        ): ProductWebChromeClient
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        consoleMessage?.let {
            val message = "$logPrefix: ${it.message()}"
            it.messageLevel().timberLog(message)
        }
        return true
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrEmpty()) {
            onTitleReceived?.invoke(title)
        }
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        scope.launch {
            val productId = callingProductIdProvider.getProductIdOrNull()
            if (productId == null) {
                Timber.w("$logPrefix: denying PermissionRequest — no calling product")
                request.deny()
                return@launch
            }

            val grantedResources = request.resources.filter { resource ->
                val capability = resource.toDeviceCapability()
                if (capability == null) {
                    Timber.w("$logPrefix: skipping unsupported PermissionRequest resource '$resource'")
                    return@filter false
                }
                consumeCapability(productId, capability)
            }

            if (grantedResources.isNotEmpty()) {
                request.grant(grantedResources.toTypedArray())
            } else {
                request.deny()
            }
        }
    }

    private suspend fun consumeCapability(productId: ProductId, capability: DeviceCapabilityType): Boolean {
        val permission = ProductPermission.DeviceCapability(capability)
        return permissionGuard.consumePermission(productId, permission)
    }
}

private fun String.toDeviceCapability(): DeviceCapabilityType? = when (this) {
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> DeviceCapabilityType.Camera
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> DeviceCapabilityType.Microphone
    else -> null
}

private fun ConsoleMessage.MessageLevel?.timberLog(message: String) {
    when (this) {
        ConsoleMessage.MessageLevel.TIP -> Timber.v(message)
        ConsoleMessage.MessageLevel.LOG -> Timber.d(message)
        ConsoleMessage.MessageLevel.WARNING -> Timber.w(message)
        ConsoleMessage.MessageLevel.ERROR -> Timber.e(message)
        ConsoleMessage.MessageLevel.DEBUG -> Timber.d(message)
        null -> Timber.d(message)
    }
}
