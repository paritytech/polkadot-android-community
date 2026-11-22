# Device authentication and confirmation gates

## Overview

Device authentication asks the person holding the phone for biometrics or the device screen lock (PIN, pattern or password). The app uses it at each control that confirms a transfer, a signing, a device pairing, a product approval or a manual backup of the recovery phrase.

Why: an unlocked phone must not be enough to sign, pay, pair or export a secret.

The gate sits at the confirm control, not at key access. Keys stay readable without device authentication.

## Rules at a glance

1. **`major`** — A new confirm control for signing, payment, pairing or secret export calls `BiometricsService.performOneTimeAuthentication()` before its action runs, and runs the action only on success.
2. **`major`** — Gates use `performOneTimeAuthentication()`, never `performSessionAuthentication()`. A past authentication never counts toward a later gate.

## Confirmation gates

### Rule

On a confirm tap, the app shows the device authentication prompt before the action runs. On success the action runs as before. On a cancel or a failure nothing runs, the screen stays in its pre-tap state, and no new error shows.

Each gated action prompts every time. A request made while a prompt is live fails at once and runs nothing.

With no screen lock set, the existing helper opens the system enrolment settings. The action runs only if the device is secure afterwards and the prompt then succeeds.

### Gated actions

| Action | Gate location |
|--------|---------------|
| Transfer submit, after validation and any privacy confirmation | `SendEnterAmountViewModel.onConfirmClick` |
| Signing sheet approve (host API, SSO and VRF signing) | `TransactionSignViewModel.onApproveClicked` |
| Device pairing approve | `PairRequestViewModel.onApproveClicked` |
| Cross-product proof approve | `CrossProductProofViewModel.onApproveClicked` |
| Product payment request, Confirm step only | `PaymentRequestViewModel.onApproveClicked` |
| Manual Google Drive backup of the recovery phrase | `BackupStatusViewModel.onCreateBackup` |
| Overriding a conflicting Drive backup | `BackupConflictViewModel.confirmOverride` |

A product on the payment whitelist has no Confirm step, so its payment request shows no prompt. On release builds this covers the funding products, onramp and offramp included (`WhitelistedProductsProvider`).

### Where the gate goes

The confirm handler in the ViewModel calls the gate and wraps its existing body in `onSuccess`. The ViewModel injects `BiometricsService` directly. A screen whose interactor already exposes the call, like `BackupStatusInteractor.performOneTimeAuthentication`, keeps using it.

A cancel needs no handling of its own. Every prompt error becomes `AuthenticationCancelledException`, a `UserCancellation`, which `showPresentationError` drops.

### Helper behaviour

The helper is `RealBiometricsService.performOneTimeAuthentication` in `tools/biometrics/impl`.

- A request while a prompt is live fails at once with `AuthenticationCancelledException`.
- An authenticator that throws, for example with no activity attached, becomes a failure instead of an exception. That failure is not a `UserCancellation`, so a handler with an error path shows it. A tap always has an activity, so this does not happen from a confirm control.
- If the system cancels a live prompt, for example when the app leaves the foreground, the prompt reports an error, which maps to a cancel. The screen stays in its pre-tap state.
