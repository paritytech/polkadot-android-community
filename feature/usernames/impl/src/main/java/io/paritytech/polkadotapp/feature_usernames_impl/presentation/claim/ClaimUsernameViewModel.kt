package io.paritytech.polkadotapp.feature_usernames_impl.presentation.claim

import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.feature_backup_api.presentation.BackupFoundPayload
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.AccountOnboardingStatus
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username
import io.paritytech.polkadotapp.feature_usernames_api.presentation.MIN_USERNAME_LENGTH
import io.paritytech.polkadotapp.feature_usernames_api.presentation.model.DigitsFieldState
import io.paritytech.polkadotapp.feature_usernames_api.presentation.model.UsernameFieldState
import io.paritytech.polkadotapp.feature_usernames_impl.domain.interactor.UsernamesClaimInteractor
import io.paritytech.polkadotapp.feature_usernames_impl.domain.model.UsernameAvailabilityState
import io.paritytech.polkadotapp.feature_usernames_impl.presentation.UsernamesRouter
import io.paritytech.polkadotapp.feature_web3summit_api.presentation.PostOnboardingFlow
import io.paritytech.polkadotapp.tools_backup_api.domain.model.BackupOutcome
import io.paritytech.polkadotapp.tools_integrity_api.exception.IntegrityException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

private const val MAX_USERNAME_LENGTH = 29
private const val MAX_DIGITS_LENGTH = 2

@HiltViewModel
class ClaimUsernameViewModel @Inject constructor(
    private val router: UsernamesRouter,
    private val interactor: UsernamesClaimInteractor,
    private val postOnboardingFlow: PostOnboardingFlow,
) : BaseViewModel(), ClaimUsernameContract {
    override val state = MutableStateFlow(ClaimUsernameState())

    init {
        observeUsernameChanges()
        // All the logic is concentrated in this method,
        // which observes the chain and local storage for when an account
        // has been created and the username has been claimed
        observeOnboardingState()
    }

    private fun observeOnboardingState() {
        interactor.observeAccountOnboardingStatus()
            .onEach { status -> handleOnboardingStatus(status) }
            .launchIn(this)
    }

    private suspend fun handleOnboardingStatus(status: AccountOnboardingStatus) {
        when {
            status.isOnboarded -> postOnboardingFlow.openPostOnboarding()
            status.accountCreated -> {
                state.update { it.copy(showRecoverOption = false) }
                tryRecoverUsername()
            }

            else -> {
                state.update { it.copy(showRecoverOption = true) }
            }
        }
    }

    private suspend fun tryRecoverUsername() {
        interactor.recoverUsername()
            .onSuccess { usernameFound ->
                if (!usernameFound) {
                    interactor.saveIsNewAccount()
                }
            }
            .onFailure {
                showError(it)
                state.update { it.copy(progress = ClaimUsernameProgress.NONE) }
            }
    }

    private fun meetsMinimumLength(name: String) = name.length >= MIN_USERNAME_LENGTH

    private fun observeUsernameChanges() {
        state.map { it.username }
            .distinctUntilChanged()
            .debounce(300.milliseconds)
            .filter(::meetsMinimumLength)
            .mapLatest { username ->
                state.update {
                    it.copy(
                        fieldState = UsernameFieldState.NEUTRAL,
                        availableDigits = emptyList(),
                        digitsFieldState = DigitsFieldState.Hidden
                    )
                }
                interactor.checkUsernameAvailable(Username.fromParts(username, null))
                    .onSuccess { availabilityState ->
                        when (availabilityState) {
                            is UsernameAvailabilityState.Available -> {
                                val firstDigits = availabilityState.availableDigits.firstOrNull().orEmpty()
                                state.update {
                                    it.copy(
                                        fieldState = UsernameFieldState.AVAILABLE,
                                        availableDigits = availabilityState.availableDigits,
                                        digitsFieldState = if (firstDigits.isNotEmpty()) {
                                            DigitsFieldState.Visible(digits = firstDigits, isValid = true)
                                        } else {
                                            DigitsFieldState.Hidden
                                        }
                                    )
                                }
                            }

                            is UsernameAvailabilityState.Taken -> {
                                state.update { it.copy(fieldState = UsernameFieldState.TAKEN) }
                            }

                            is UsernameAvailabilityState.Invalid -> {
                                state.update { it.copy(fieldState = UsernameFieldState.INVALID) }
                            }
                        }
                    }
                    .onFailure { Timber.d(it) }
            }
            .launchIn(this)
    }

    override fun backPressed() {
        router.back()
    }

    override fun onUsernameChanged(value: String) {
        val previousValue = state.value.username
        val newValue = value.filterUsernameInput()

        if (previousValue != newValue) {
            state.update {
                it.copy(
                    username = newValue,
                    fieldState = UsernameFieldState.NEUTRAL,
                    availableDigits = emptyList(),
                    digitsFieldState = DigitsFieldState.Hidden
                )
            }
        }
    }

    /** Validates entered digits client-side against the stored [ClaimUsernameState.availableDigits] list. */
    override fun onDigitsChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(MAX_DIGITS_LENGTH)
        val availableDigits = state.value.availableDigits
        val isValid = filtered.length == MAX_DIGITS_LENGTH && filtered in availableDigits
        state.update {
            it.copy(digitsFieldState = DigitsFieldState.Visible(digits = filtered, isValid = isValid))
        }
    }

    override fun onClaimClicked() {
        launch {
            if (interactor.areAccountsInitialized()) {
                claimUsername(ClaimUsernameProgress.CREATING)
            } else {
                state.update { it.copy(progress = ClaimUsernameProgress.CLAIMING) }

                interactor.tryRecoverBackupOrCreateAccount()
                    .onSuccess { handleOutcome(it) }
                    .onFailure { error ->
                        state.update { it.copy(progress = ClaimUsernameProgress.NONE) }
                        showError(error)
                    }
            }
        }
    }

    private fun handleOutcome(outcome: BackupOutcome) {
        when (outcome) {
            BackupOutcome.Created,
            BackupOutcome.AccountsCreatedButBackupFailed,
            BackupOutcome.NoNeedToBackup -> claimUsername(ClaimUsernameProgress.CREATING)

            is BackupOutcome.ExistingBackupFound -> {
                state.update { it.copy(progress = ClaimUsernameProgress.NONE) }
                router.openBackupFound(
                    BackupFoundPayload(outcome.createdAt, outcome.accountId.value)
                )
            }
        }
    }

    private fun claimUsername(progress: ClaimUsernameProgress) {
        state.update { it.copy(progress = progress) }

        launch {
            val baseUsername = Username.fromParts(state.value.username, null)
            val preferredDigits = (state.value.digitsFieldState as? DigitsFieldState.Visible)?.digits.orEmpty()
            val isAvailable = interactor.checkUsernameAvailable(baseUsername)
                .map { it is UsernameAvailabilityState.Available }
                .getOrDefault(false)

            if (isAvailable) {
                interactor.claimUsername(baseUsername, preferredDigits)
                    .onFailure {
                        state.update { it.copy(progress = ClaimUsernameProgress.NONE) }
                        handleClaimError(it)
                    }
            } else {
                state.update { it.copy(progress = ClaimUsernameProgress.NONE, fieldState = UsernameFieldState.TAKEN) }
            }
        }
    }

    private fun handleClaimError(error: Throwable) {
        when (error) {
            is IntegrityException -> {
                state.update {
                    it.copy(fieldState = UsernameFieldState.ALREADY_CREATED)
                }
            }

            else -> showError(error)
        }
    }

    override fun onClearAction() {
        state.update {
            it.copy(
                username = "",
                fieldState = UsernameFieldState.NEUTRAL,
                availableDigits = emptyList(),
                digitsFieldState = DigitsFieldState.Hidden
            )
        }
    }

    override fun onRecoverClicked() {
        if (state.value.progress != ClaimUsernameProgress.NONE) return

        router.openRecoverOptions()
    }

    override fun onTermsClicked() {
        router.openTermsOfUse()
    }

    override fun onPrivacyPolicyClicked() {
        router.openPrivacyPolicy()
    }

    override fun onBackupOverridden() {
        if (meetsMinimumLength(state.value.username)) {
            claimUsername(ClaimUsernameProgress.CREATING)
        }
    }

    override fun onImportedFromBackup() {
        state.update { it.copy(progress = ClaimUsernameProgress.RECOVERING) }
    }
}

fun String.filterUsernameInput(): String = filter { it.isLetter() }
    .take(MAX_USERNAME_LENGTH)
    .lowercase()
