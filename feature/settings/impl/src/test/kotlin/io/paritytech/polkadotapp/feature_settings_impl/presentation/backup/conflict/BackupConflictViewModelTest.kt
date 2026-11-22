package io.paritytech.polkadotapp.feature_settings_impl.presentation.backup.conflict

import androidx.lifecycle.SavedStateHandle
import io.paritytech.polkadotapp.feature_backup_api.presentation.BackupConflictPayload
import io.paritytech.polkadotapp.feature_settings_impl.SettingsRouter
import io.paritytech.polkadotapp.feature_settings_impl.domain.interactors.BackupConflictInteractor
import io.paritytech.polkadotapp.feature_settings_impl.presentation.backup.conflict.models.BackupConflictStep
import io.paritytech.polkadotapp.test_shared.assertNothingShown
import io.paritytech.polkadotapp.test_shared.whenever
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationCancelledException
import io.paritytech.polkadotapp.tools_authentication_api.domain.BiometricsService
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
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private const val BACKUP_CREATED_AT = 1_700_000_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class BackupConflictViewModelTest {
    private val router: SettingsRouter = mock(SettingsRouter::class.java)
    private val interactor: BackupConflictInteractor = mock(BackupConflictInteractor::class.java)
    private val biometricsService: BiometricsService = mock(BiometricsService::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `overrides the backup when authentication succeeds`() = runBlocking<Unit> {
        withAuthenticationSucceeding()
        withBackupOverridden()
        val viewModel = createViewModel()
        viewModel.proceedToOverride()

        viewModel.confirmOverride()

        verifyOverriddenThenClosedWithResult()
    }

    @Test
    fun `does not override when authentication is cancelled`() = runBlocking<Unit> {
        withAuthenticationCancelled()
        val viewModel = createViewModel()
        viewModel.proceedToOverride()

        viewModel.confirmOverride()

        verifyNoOverride()
        assertEquals(BackupConflictStep.Override(inProgress = false), viewModel.step.value)
        assertNothingShown(viewModel)
    }

    private suspend fun withAuthenticationSucceeding() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.success(Unit))
    }

    private suspend fun withAuthenticationCancelled() {
        whenever(biometricsService.performOneTimeAuthentication()).thenReturn(Result.failure(AuthenticationCancelledException()))
    }

    private suspend fun withBackupOverridden() {
        whenever(interactor.overrideBackup()).thenReturn(Result.success(Unit))
    }

    private suspend fun verifyOverriddenThenClosedWithResult() {
        val order = inOrder(interactor, router)
        order.verify(interactor, times(1)).overrideBackup()
        order.verify(router).backWithResult(BackupConflictBottomSheet.REQUEST_KEY, true)
    }

    private suspend fun verifyNoOverride() {
        verify(interactor, never()).overrideBackup()
    }

    private fun createViewModel() = BackupConflictViewModel(
        router = router,
        interactor = interactor,
        biometricsService = biometricsService,
        savedStateHandle = SavedStateHandle(
            mapOf(BackupConflictPayload::class.java.name to BackupConflictPayload(BACKUP_CREATED_AT))
        ),
    )
}
