package io.paritytech.polkadotapp.tools_media_connection_impl.utils

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

suspend fun PeerConnection.createOffer() = suspendCancellableCoroutine {
    createOffer(
        onCreateSdpObserver(SessionDescription.Type.OFFER, it),
        MediaConstraints()
    )
}

suspend fun PeerConnection.createAnswer() = suspendCancellableCoroutine {
    createAnswer(
        onCreateSdpObserver(SessionDescription.Type.ANSWER, it),
        MediaConstraints()
    )
}

suspend fun PeerConnection.setLocalDescription(sdp: SessionDescription) = suspendCancellableCoroutine {
    setLocalDescription(
        onSetSdpObserver(sdp.type, it),
        sdp
    )
}

suspend fun PeerConnection.setRemoteDescription(
    sdp: SessionDescription
) = suspendCancellableCoroutine { continuation ->
    setRemoteDescription(
        onSetSdpObserver(sdp.type, continuation),
        sdp
    )
}

suspend fun PeerConnection.addCandidate(candidate: IceCandidate) = suspendCancellableCoroutine { continuation ->
    addIceCandidate(
        candidate,
        onAddCandidateObserver(continuation)
    )
}

suspend fun PeerConnection.addCandidates(candidates: List<IceCandidate>) {
    candidates.forEach { addCandidate(it) }
}

suspend fun PeerConnection.awaitRemoteSdpSet() {
    withTimeout(10.seconds) {
        while (remoteDescription == null) {
            delay(100)
        }
    }
}

private fun onCreateSdpObserver(type: SessionDescription.Type, continuation: Continuation<SessionDescription>) = object : SimpleSdpObserver() {
    override fun onCreateSuccess(sdp: SessionDescription) {
        continuation.resume(sdp)
    }

    override fun onCreateFailure(error: String) {
        continuation.resumeWithException(RuntimeException("Failed to create SDP $type: $error"))
    }
}

private fun onSetSdpObserver(type: SessionDescription.Type, continuation: Continuation<Unit>): SimpleSdpObserver = object : SimpleSdpObserver() {
    override fun onSetSuccess() {
        continuation.resume(Unit)
    }

    override fun onSetFailure(error: String) {
        continuation.resumeWithException(Exception("Failed to set SDP $type: $error"))
    }
}

private fun onAddCandidateObserver(continuation: Continuation<Unit>) = object : AddIceObserver {
    override fun onAddSuccess() {
        continuation.resume(Unit)
    }

    override fun onAddFailure(error: String) {
        continuation.resumeWithException(Exception("Failed to add ICE candidate: $error"))
    }
}

fun createPeerConnectionFactory(context: Context, eglBase: EglBase): PeerConnectionFactory {
    val options = PeerConnectionFactory
        .InitializationOptions
        .builder(context)
        .createInitializationOptions()

    PeerConnectionFactory.initialize(options)

    val audioDeviceModule = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(true)
        .setUseHardwareNoiseSuppressor(true)
        .setUseLowLatency(true)
        .createAudioDeviceModule()

    val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
    val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

    return PeerConnectionFactory.builder()
        .setAudioDeviceModule(audioDeviceModule)
        .setVideoEncoderFactory(encoderFactory)
        .setVideoDecoderFactory(decoderFactory)
        .createPeerConnectionFactory()
}
