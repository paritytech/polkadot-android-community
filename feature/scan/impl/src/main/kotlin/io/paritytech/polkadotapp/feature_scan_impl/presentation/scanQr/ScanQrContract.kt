package io.paritytech.polkadotapp.feature_scan_impl.presentation.scanQr

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ScanQrContract {
    val surfaceRequest: StateFlow<SurfaceRequest?>

    val invalidCodeEvent: SharedFlow<Unit>

    fun back()

    fun invalidationDialogClosed()

    suspend fun bindToCamera(lifecycleOwner: LifecycleOwner)
}
