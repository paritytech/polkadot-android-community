package io.paritytech.polkadotapp.feature_scan_impl.presentation.scanQr

import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.camera.CameraQrReader
import io.paritytech.polkadotapp.common.presentation.camera.QrCodeAnalyzer
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.emit
import io.paritytech.polkadotapp.common.utils.launchUnit
import io.paritytech.polkadotapp.feature_scan_api.domain.PostParseAction
import io.paritytech.polkadotapp.feature_scan_api.domain.ScanContentParser
import io.paritytech.polkadotapp.feature_scan_impl.ScanRouter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class ScanQrViewModel @Inject constructor(
    private val router: ScanRouter,
    private val cameraQrReader: CameraQrReader,
    private val parsers: Set<@JvmSuppressWildcards ScanContentParser>,
) : BaseViewModel(), ScanQrContract {
    override val surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)

    override val invalidCodeEvent = MutableSharedFlow<Unit>()

    private var pauseDecoding = false

    override fun invalidationDialogClosed() {
        pauseDecoding = false
    }

    override suspend fun bindToCamera(lifecycleOwner: LifecycleOwner) {
        cameraQrReader.bind(
            preview = Preview.Builder().build().apply {
                setSurfaceProvider { newSurfaceRequest ->
                    surfaceRequest.value = newSurfaceRequest
                }
            },
            lifecycleOwner = lifecycleOwner,
            qrCodeAnalyzer = QrCodeAnalyzer { handleQrCodeData(it) }
        )
    }

    override fun back() {
        router.back()
    }

    private fun handleQrCodeData(data: String) {
        if (pauseDecoding) return

        pauseDecoding = true

        launchUnit {
            val parser = parsers.firstOrNull { it.canHandle(data) }
            if (parser == null) {
                invalidCodeEvent.emit()
                return@launchUnit
            }

            parser.handle(data)
                .onSuccess { handlePostParseAction(it) }
                .onFailure { invalidCodeEvent.emit() }
        }
    }

    private fun handlePostParseAction(action: PostParseAction) {
        when (action) {
            is PostParseAction.BackAndThen -> {
                router.back()
                action.postBackNavigation()
            }

            PostParseAction.Nothing -> Unit
        }
    }
}
