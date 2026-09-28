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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

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
    fun `the configured url is returned as is`() = runBlocking<Unit> {
        withConfiguredUrl(APP_SHARING_URL)

        assertEquals(Result.success(APP_SHARING_URL), interactor.getAppSharingUrl())
    }

    @Test
    fun `an unset key fails instead of yielding an empty link`() = runBlocking<Unit> {
        withConfiguredUrl("")

        assertFailure(interactor.getAppSharingUrl())
    }

    @Test
    fun `a failed config read fails`() = runBlocking<Unit> {
        withConfigReadFailing()

        assertFailure(interactor.getAppSharingUrl())
    }

    private suspend fun withConfiguredUrl(url: String) {
        whenever(remoteConfigService.getSyncedString(APP_SHARING_URL_KEY)).thenReturn(Result.success(url))
    }

    private suspend fun withConfigReadFailing() {
        whenever(remoteConfigService.getSyncedString(APP_SHARING_URL_KEY))
            .thenReturn(Result.failure(IllegalStateException("not synced")))
    }

    private fun assertFailure(result: Result<String>) {
        assertTrue("expected a failure but was $result", result.isFailure)
    }
}
