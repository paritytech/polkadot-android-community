# Device authentication and confirmation gates

Device authentication asks the person holding the phone for biometrics or the device screen lock (PIN, pattern or password). The app uses it at the controls that confirm a payment at or above the payment threshold, a signing with the identity account, a device pairing, a cross-product proof or a manual backup of the recovery phrase.

Why: an unlocked phone must not be enough to sign as the user, make a large payment, pair or export a secret.

The gate sits at the confirm control, not at key access. Keys stay readable without device authentication.

## Rules at a glance

1. **`major`** — A new confirm control for signing with the identity account, a cross-product proof, pairing or secret export calls `BiometricsService.performOneTimeAuthentication()` before its action runs, and runs the action only on success.
2. **`major`** — Gates use `performOneTimeAuthentication()`, never `performSessionAuthentication()`. A past authentication never counts toward a later gate.
3. **`major`** — A control that spends CASH calls `PaymentAuthenticationUseCase.authenticateIfRequired(amount)` instead, so only amounts at or above the payment threshold prompt.

## Confirmation gates

### Rule

On a tap of a gated control, the app shows the device authentication prompt before the action runs. On success the action runs as before. On a cancel or a failure nothing runs and the screen stays in its pre-tap state. A cancel shows nothing. A failure, such as no screen lock, missing hardware or a prompt error other than a cancel, shows `AuthenticationFailedPresentationError`.

Each gated action prompts on every tap that meets its condition in the table below. A request made while a prompt is live fails at once and runs nothing.

With no screen lock set, the existing helper opens the system enrolment settings. The action runs only if the device is secure afterwards and the prompt then succeeds. Otherwise the gate fails.

### Gated actions

| Action | Gate location |
|--------|---------------|
| Transfer submit at or above the payment threshold, after validation and any privacy confirmation | `SendEnterAmountViewModel.onConfirmClick` |
| Signing sheet approve with the identity account (host API and SSO signing) | `TransactionSignViewModel.onApproveClicked` |
| Device pairing approve | `PairRequestViewModel.onApproveClicked` |
| Cross-product proof approve | `CrossProductProofViewModel.onApproveClicked` |
| Product payment request at or above the payment threshold, Confirm step only | `PaymentRequestViewModel.onApproveClicked` |
| Manual Google Drive backup of the recovery phrase | `BackupStatusViewModel.onCreateBackup` |
| Overriding a conflicting Drive backup | `BackupConflictViewModel.confirmOverride` |

Only the Confirm step of a payment request prompts. A request without a Confirm step shows no prompt.

Signing with a product's own account, VRF signing included, does not prompt. Only `SigningAccount.IdentityAccount` signing does. The rule is `SigningAccount.requiresDeviceAuthentication` in `feature/products/impl`.

### Payment threshold

A transfer or a product payment request prompts only when its amount is at least half of `CoinageBalance.total` or 50 CASH, whichever is lower. A smaller amount runs without a prompt. If the balance cannot be read, the gate prompts. The rule is `RealPaymentAuthenticationUseCase` in `feature/coinage/impl`.

### Where the gate goes

The confirm handler in the ViewModel calls the gate and wraps its existing body in `BaseViewModel.onAuthenticated`, which runs it on success and shows `AuthenticationFailedPresentationError` on failure. The ViewModel injects `BiometricsService` directly. A payment goes through `PaymentAuthenticationUseCase`, called from the screen's interactor (`SendEnterAmountInteractor.authenticateIfRequired`) or, on a screen without an interactor, injected into the ViewModel (`PaymentRequestViewModel`). A screen whose interactor already exposes the call, like `BackupStatusInteractor.performOneTimeAuthentication`, keeps using it.

A cancel arrives as `AuthenticationCancelledException`, a `UserCancellation`, which `showPresentationError` drops. The prompt errors that count as a cancel are `ERROR_USER_CANCELED`, `ERROR_NEGATIVE_BUTTON` and `ERROR_CANCELED` (`biometricPromptError`). Any other prompt error arrives as `AuthenticationFailedException`, which it shows.

### Helper behaviour

The helper is `RealBiometricsService.performOneTimeAuthentication` in `tools/biometrics/impl`.

- A request while a prompt is live fails at once with `AuthenticationCancelledException`.
- `BiometricPromptAuthenticationMethod` never throws. With no activity attached it returns `AuthenticationFailedException`; a tap always has an activity, so a confirm control never hits this. Any other exception from the prompt or the enrolment launch, such as `ActivityNotFoundException` on a device with no enrolment screen, becomes a failure too, so the gate shows it.
- If the system cancels a live prompt, for example when the app leaves the foreground, the prompt reports an error, which maps to a cancel. The screen stays in its pre-tap state.
- A request made after the activity saved its state, for example when validation finishes after the app left the foreground, fails at once as a cancel. `BiometricPrompt` would otherwise drop it without a callback.
