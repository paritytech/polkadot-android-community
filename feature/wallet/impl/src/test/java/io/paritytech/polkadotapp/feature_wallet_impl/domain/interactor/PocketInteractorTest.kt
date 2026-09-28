package io.paritytech.polkadotapp.feature_wallet_impl.domain.interactor

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageAccountBackupObserver
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageBackupService
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TotalBalanceUseCase
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.UsernameOfAccountUseCase
import io.paritytech.polkadotapp.feature_videogame_api.domain.state.VideoGamesProgressUseCase
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_remoteconfig_api.RemoteConfigService
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.invocation.Invocation
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine

private const val APP_SHARING_URL_KEY = "app_sharing_url"
private const val APP_SHARING_URL = "https://example.com/app"

class PocketInteractorTest {
    private val remoteConfigService: RemoteConfigService = mock(RemoteConfigService::class.java)

    private val interactor = PocketInteractor(
        chainAssetProvider = mock(ChainAssetProvider::class.java),
        totalBalanceUseCase = mock(TotalBalanceUseCase::class.java),
        usernameOfAccountUseCase = mock(UsernameOfAccountUseCase::class.java),
        gamesProgressUseCase = mock(VideoGamesProgressUseCase::class.java),
        coinageBackupService = mock(CoinageBackupService::class.java),
        coinageAccountBackupObserver = mock(CoinageAccountBackupObserver::class.java),
        accountRepository = mock(AccountRepository::class.java),
        chainRegistry = mock(ChainRegistry::class.java),
        knownChains = KnownChains(people = "people", assetHub = "asset-hub", bulletIn = "bulletin", hydration = null),
        remoteConfigService = remoteConfigService,
    )

    @Test
    fun `returns the synced url when it is set`() = runBlocking<Unit> {
        withSyncedUrl(APP_SHARING_URL)

        assertEquals(Result.success(APP_SHARING_URL), interactor.getAppSharingUrl())
    }

    @Test
    fun `fails when the key is unset`() = runBlocking<Unit> {
        withSyncedUrl("")

        assertFailure(interactor.getAppSharingUrl())
    }

    @Test
    fun `fails when the session never syncs`() = runBlocking<Unit> {
        withSyncNeverCompleting()

        assertFailure(interactor.getAppSharingUrl())
    }

    @Test
    fun `fails when the synced read fails`() = runBlocking<Unit> {
        withSyncedReadFailing()

        assertFailure(interactor.getAppSharingUrl())
    }

    private suspend fun withSyncedUrl(url: String) {
        whenever(remoteConfigService.getSyncedString(APP_SHARING_URL_KEY)).thenReturn(Result.success(url))
    }

    private suspend fun withSyncedReadFailing() {
        whenever(remoteConfigService.getSyncedString(APP_SHARING_URL_KEY))
            .thenReturn(Result.failure(IllegalStateException("not synced")))
    }

    private suspend fun withSyncNeverCompleting() {
        whenever(remoteConfigService.getSyncedString(APP_SHARING_URL_KEY)).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val continuation = (invocation as Invocation).rawArguments.last() as Continuation<Result<String>>
            val waitForever: suspend () -> Result<String> = { awaitCancellation() }
            waitForever.startCoroutine(continuation)
            COROUTINE_SUSPENDED
        }
    }

    private fun assertFailure(result: Result<String>) {
        assertTrue("expected a failure but was $result", result.isFailure)
    }
}
