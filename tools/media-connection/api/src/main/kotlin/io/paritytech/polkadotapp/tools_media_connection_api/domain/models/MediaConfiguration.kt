package io.paritytech.polkadotapp.tools_media_connection_api.domain.models

sealed interface MediaConfiguration {
    data object None : MediaConfiguration
    data object VideoOnly : MediaConfiguration
    data class AudioVideo(
        val initialCameraEnabled: Boolean,
        val initialMicrophoneEnabled: Boolean
    ) : MediaConfiguration
}

fun MediaConfiguration.shouldHaveVideo(): Boolean {
    return this is MediaConfiguration.VideoOnly || this is MediaConfiguration.AudioVideo
}
