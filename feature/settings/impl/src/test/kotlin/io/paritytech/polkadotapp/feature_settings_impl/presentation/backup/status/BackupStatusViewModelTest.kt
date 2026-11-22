package io.paritytech.polkadotapp.feature_settings_impl.presentation.backup.status

import io.paritytech.polkadotapp.feature_settings_impl.SettingsRouter
import io.paritytech.polkadotapp.feature_settings_impl.domain.interactors.BackupState
import io.paritytech.polkadotapp.feature_settings_impl.domain.interactors.BackupStatusInteractor
import io.paritytech.polkadotapp.feature_settings_impl.presentation.backup.status.models.BackupStatusUiState
import io.paritytech.polkadotapp.test_shared.assertNothingShown
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class BackupStatusViewModelTest {
    private val router: SettingsRouter = mock(SettingsRouter::class.java)
    private val interactor: BackupStatusInteractor = mock(BackupStatusInteractor::class.java)

    @Before
    fun setUp() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        withNoExistingBackup()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `writes the backup when authentication succeeds`() = runBlocking<Unit> {
        withAuthenticationSucceeding()
        withBackupSaved()
        val viewModel = createViewModel()

        viewModel.onCreateBackup()

        verifyBackupSavedOnce()
        assertEquals(BackupStatusUiState.BackupExists, viewModel.state.value)
    }

    @Test
    fun `writes nothing and shows no error when authentication is cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val viewModel = createViewModel()

        viewModel.onCreateBackup()

        verifyNoBackupSaved()
        assertNothingShown(viewModel)
    }

    @Test
    fun `stays on NoBackup when authentication is cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val viewModel = createViewModel()

        viewModel.onCreateBackup()

        assertEquals(BackupStatusUiState.NoBackup, viewModel.state.value)
    }

    private suspend fun withNoExistingBackup() {
        whenever(interactor.resolveBackupState()).thenReturn(BackupState.None)
    }

    private suspend fun withAuthenticationSucceeding() {
        whenever(interactor.performOneTimeAuthentication()).thenReturn(Result.success(Unit))
    }

    private suspend fun withAuthenticationCancelled() {
        whenever(interactor.performOneTimeAuthentication()).thenReturn(Result.failure(AuthenticationCancelledException()))
    }

    private suspend fun withBackupSaved() {
        whenever(interactor.saveBackup()).thenReturn(Result.success(Unit))
    }

    private suspend fun verifyBackupSavedOnce() {
        verify(interactor, times(1)).saveBackup()
    }

    private suspend fun verifyNoBackupSaved() {
        verify(interactor, never()).saveBackup()
    }

    private fun createViewModel() = BackupStatusViewModel(
        router = router,
        interactor = interactor,
    )
}
