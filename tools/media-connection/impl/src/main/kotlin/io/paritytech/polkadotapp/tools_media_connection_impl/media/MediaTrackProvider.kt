package io.paritytech.polkadotapp.tools_media_connection_impl.media

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import androidx.core.content.getSystemService
import io.paritytech.polkadotapp.common.data.memory.SingleValueCache
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Capturer
import org.webrtc.Camera2Enumerator
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

private const val FPS = 30

interface MediaTrackProvider {
    suspend fun getOrCreateVideoTrack(): VideoTrack
    suspend fun getOrCreateAudioTrack(): AudioTrack
    fun setVideoEnabled(enabled: Boolean)
    fun setAudioEnabled(enabled: Boolean)
    fun dispose()
}

class DefaultMediaTrackProvider(
    private val context: Context,
    private val eglBase: EglBase,
    private val peerConnectionFactory: PeerConnectionFactory
) : MediaTrackProvider {
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    private var captureWidth: Int = 0
    private var captureHeight: Int = 0

    private val videoTrackCache = SingleValueCache { createVideoTrack() }
    private val audioTrackCache = SingleValueCache { createAudioTrack() }

    override suspend fun getOrCreateVideoTrack(): VideoTrack = videoTrackCache()

    override suspend fun getOrCreateAudioTrack(): AudioTrack = audioTrackCache()

    private suspend fun createVideoTrack(): VideoTrack {
        val textureHelper = SurfaceTextureHelper.create(
            "SurfaceTextureHelperThread",
            eglBase.eglBaseContext
        )
        surfaceTextureHelper = textureHelper

        val manager = context.getSystemService<CameraManager>()
            ?: throw RuntimeException("CameraManager was not initialized!")

        val cameraId = findBestCameraId(manager)
        val enumerator = Camera2Enumerator(context)

        val supportedFormats = enumerator.getSupportedFormats(cameraId) ?: emptyList()
        val format720p = supportedFormats.first { (it.width == 720 || it.height == 720) }
        captureWidth = format720p.width
        captureHeight = format720p.height

        val capturer = Camera2Capturer(context, cameraId, null)

        val source = peerConnectionFactory.createVideoSource(false).apply {
            capturer.initialize(textureHelper, context, capturerObserver)
        }
        videoSource = source

        videoCapturer = capturer

        val track = peerConnectionFactory.createVideoTrack("video0", source)
        localVideoTrack = track
        return track
    }

    override fun setVideoEnabled(enabled: Boolean) {
        val track = localVideoTrack ?: return

        if (enabled) {
            videoCapturer?.startCapture(captureWidth, captureHeight, FPS)
            track.setEnabled(true)
        } else {
            track.setEnabled(false)
            videoCapturer?.stopCapture()
        }
    }

    override fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    private fun createAudioTrack(): AudioTrack {
        val source = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioSource = source
        val track = peerConnectionFactory.createAudioTrack("audio0", source)
        localAudioTrack = track
        return track
    }

    override fun dispose() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localAudioTrack?.dispose()
        localVideoTrack?.dispose()
        audioSource?.dispose()
        videoSource?.dispose()
        surfaceTextureHelper?.dispose()
    }

    private fun findBestCameraId(manager: CameraManager): String? = manager.cameraIdList
        .mapNotNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val cameraLensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            if (cameraLensFacing == CameraMetadata.LENS_FACING_FRONT) {
                val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val focalLength = focalLengths?.firstOrNull()
                if (focalLength != null) {
                    id to focalLength
                } else {
                    null
                }
            } else {
                null
            }
        }
        .minByOrNull { it.second }
        ?.first
        ?: manager.cameraIdList.firstOrNull()
}
