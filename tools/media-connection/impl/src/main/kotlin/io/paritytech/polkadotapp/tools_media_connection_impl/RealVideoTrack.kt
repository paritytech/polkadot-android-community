package io.paritytech.polkadotapp.tools_media_connection_impl

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.getstream.webrtc.android.ui.VideoTextureViewRenderer
import io.paritytech.polkadotapp.tools_media_connection_api.domain.models.VideoTrack
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack as WebRtcVideoTrack

class RealVideoTrack(
    private val webrtcTrack: WebRtcVideoTrack,
    private val eglContext: EglBase.Context
) : VideoTrack {
    private var viewRenderer: VideoTextureViewRenderer? = null

    @Composable
    override fun Render(
        modifier: Modifier,
        isMirrored: Boolean,
        onFirstFrameRendered: (() -> Unit)?,
        onFrameResolutionChanged: ((videoWidth: Int, videoHeight: Int, rotation: Int) -> Unit)?
    ) {
        val rendererEvents = remember(onFirstFrameRendered, onFrameResolutionChanged) {
            object : RendererCommon.RendererEvents {
                override fun onFirstFrameRendered() {
                    onFirstFrameRendered?.invoke()
                }

                override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                    onFrameResolutionChanged?.invoke(videoWidth, videoHeight, rotation)
                }
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                VideoTextureViewRenderer(context).apply {
                    init(eglContext, rendererEvents)
                    setMirror(isMirrored)
                    webrtcTrack.addSink(this)
                    viewRenderer = this
                }
            },
            onRelease = { renderer ->
                dispose()
                webrtcTrack.removeSink(renderer)
                viewRenderer = null
            }
        )
    }

    override fun captureFrame(): Bitmap? {
        return viewRenderer?.bitmap
    }

    override fun dispose() {
        viewRenderer?.let { webrtcTrack.removeSink(it) }
        viewRenderer = null
    }
}
