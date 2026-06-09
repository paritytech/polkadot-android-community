package io.paritytech.polkadotapp.tools_media_connection_impl.media

import android.content.Context
import org.webrtc.AudioTrack
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoTrack

class SharedMediaTrackProvider(
    context: Context,
    eglBase: EglBase,
    peerConnectionFactory: PeerConnectionFactory
) : MediaTrackProvider {
    private val default = DefaultMediaTrackProvider(context, eglBase, peerConnectionFactory)

    override suspend fun getOrCreateVideoTrack(): VideoTrack = default.getOrCreateVideoTrack()

    override suspend fun getOrCreateAudioTrack(): AudioTrack = default.getOrCreateAudioTrack()

    override fun setVideoEnabled(enabled: Boolean) = default.setVideoEnabled(enabled)

    override fun setAudioEnabled(enabled: Boolean) = default.setAudioEnabled(enabled)

    override fun dispose() {
        default.dispose()
    }
}
