package io.paritytech.polkadotapp.tools_authentication_api.domain

interface BiometricsService {
    /**
     * Performs a once-per-session authentication
     * The function will immediately return if user has already authenticated in the current app session
     */
    suspend fun performSessionAuthentication(): Result<Unit>

    /**
     * Prompts on every call and never throws. A cancel, or a call while another prompt is live, fails with
     * [AuthenticationCancelledException]. A prompt or setup failure fails with [AuthenticationFailedException], such as
     * no screen lock, or with the exception that caused it, such as a missing enrolment screen.
     */
    suspend fun performOneTimeAuthentication(): Result<Unit>
}
