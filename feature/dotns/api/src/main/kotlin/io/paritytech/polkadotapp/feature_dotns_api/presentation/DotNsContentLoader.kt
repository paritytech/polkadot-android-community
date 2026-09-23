package io.paritytech.polkadotapp.feature_dotns_api.presentation

import android.net.Uri
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsLoadProgress
import io.paritytech.polkadotapp.feature_dotns_api.domain.DotNsResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Per-WebView-session decorator over [DotNsResolver] that tracks the domain currently being
 * resolved and exposes its load progress.
 *
 * Because it *is* a [DotNsResolver] (delegating everything else), it drops into the existing
 * [DotNsWebViewClient] constructor slot unchanged — `resolveToLocalFile`/`resolveToLocalUri` calls
 * made by the client record the requested domain as a side effect, and the host UI observes
 * [loadProgress] without the client knowing about it.
 */
class DotNsContentLoader(
    private val delegate: DotNsResolver
) : DotNsResolver by delegate {
    private val lastRequested = MutableStateFlow<Requested?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val loadProgress: Flow<DotNsLoadProgress> = lastRequested
        .flatMapLatest { requested ->
            when {
                requested == null -> flowOf(DotNsLoadProgress.Idle)
                requested.servedFromDevOrigin -> flowOf(DotNsLoadProgress.Completed)
                else -> delegate.getProgressByDomain(requested.domain)
            }
        }

    override suspend fun resolveToLocalUri(dotNsName: String): Result<Uri> {
        lastRequested.value = Requested(dotNsName, servedFromDevOrigin = false)
        return delegate.resolveToLocalUri(dotNsName)
    }

    /**
     * A domain the client proxied to a developer's machine never touches the resolver, so nothing
     * would ever mark it loaded and a host waiting for [DotNsLoadProgress.Completed] before showing
     * the WebView would spin forever. The bytes are there the moment the document is answered.
     */
    fun markServedFromDevOrigin(dotNsName: String) {
        lastRequested.value = Requested(dotNsName, servedFromDevOrigin = true)
    }

    private data class Requested(val domain: String, val servedFromDevOrigin: Boolean)
}
