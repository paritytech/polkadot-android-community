package io.paritytech.polkadotapp.tools_authentication_api.domain

interface BiometricsService {
    /**
     * Performs a once-per-session authentication
     * The function will immediately return if user has already authenticated in the current app session
     */
    suspend fun performSessionAuthentication(): Result<Unit>

    /**
     * Shows the prompt on every call, whatever happened before in the session.
     * Fails at once with [AuthenticationCancelledException] while another prompt is live, and never throws.
     */
    suspend fun performOneTimeAuthentication(): Result<Unit>
}
