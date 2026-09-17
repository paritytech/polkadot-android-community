# CI/CD Workflows

This document describes the GitHub Actions workflows under `.github/workflows`: what
each one does, what it needs from the repository settings, and where its output goes.
Building the app locally is covered by the root [README](../README.md); build-time
configuration, signing and publishing by [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md).

The workflows are the maintainers' own build and distribution flows. A fork does not
need them to build the app, and the distribution steps (Firebase App Distribution,
object storage, Allure, Matrix notifications) are tied to the maintainers' accounts, so
a fork either points them at its own services or removes them.

## Repository configuration

Configure these under **Settings → Secrets and variables → Actions**. The workflows map
them into the environment explicitly; GitHub does not expose repository configuration to
a runner on its own.

**Build-time values** — everything the Gradle build reads (`APPLICATION_ID`,
`CURRENCY_SYMBOL`, `GOOGLE_OAUTH_ID`, `NIGHTLY_FUNDING_MNEMONIC`, signing passwords, …)
is documented once, in [DEPLOYMENT §5](../docs/DEPLOYMENT.md#5-environment-variables--secrets-reference).
Public values live in Actions **variables**, credentials and mnemonics in Actions
**secrets**; the workflows only forward them as environment variables.

**Workflow-only configuration** — what the workflows read in addition to the build values:

| Name | Kind | Workflows | Purpose |
|------|------|-----------|---------|
| `GOOGLE_SERVICES_JSON_BASE64` | secret | all builds | Base64 of `google-services.json`, decoded to `app/google-services.json` by `actions/prepare-android-build`. |
| `CI_GITHUB_KEYSTORE_KEY_FILE` | secret | pr, instrumental tests, debug, nightly | Base64 of the dev keystore, decoded to `develop_key.jks`. |
| `RELEASE_GITHUB_KEYSTORE_KEY_FILE` | secret | release | Base64 of the release keystore, decoded to `release_key.jks`. |
| `SENTRY_AUTH_TOKEN` | secret | debug, nightly, release | Sentry Gradle plugin upload token (release variant is ignored by the plugin). |
| `FIREBASE_GOOGLE_SERVICE_ACCOUNT` | secret | debug, nightly, release | Service-account JSON for Firebase App Distribution. |
| `ANDROID_FIREBASE_APP_ID` | secret | debug | App Distribution app id for `gpDebug`. |
| `ANDROID_FIREBASE_RELEASE_APP_ID` | secret | release | App Distribution app id for `gpRelease`. |
| `ANDROID_FIREBASE_NIGHTLY_APP_ID` | secret | nightly | App Distribution app id for `gpNightly` and `vanillaNightly` (flavors add no `applicationIdSuffix`, so both share it). |
| `ANDROID_FIREBASE_SAFETYNET_APP_ID` | secret | nightly | App Distribution app id for `gpSafetynet`. |
| `SCW_ACCESS_KEY`, `SCW_SECRET_KEY` | secret | debug, nightly, release | Credentials for the object-storage upload (see *Build artifacts storage*). |
| `ALLURE_TOKEN` | secret | nightly | Triggers the Allure TestOps launch. |
| `NOTIFICATION_BOT_URL`, `NOTIFICATION_BOT_TOKEN` | secret | nightly | Bot that posts the release notification to Matrix. |
| `CI_MATRIX_ROOM_IDS` | variable | nightly | Comma-separated Matrix room ids that receive the notification. |
| `NIGHTLY_DOWNLOAD_LINKS` | variable | nightly | Multiline Markdown list of download links included in the notification. |

Firebase tester groups are not repository settings: they are `env` constants at the top
of each distribution workflow (`CI_FIREBASE_GROUP_MAIN`, `CI_FIREBASE_GROUP_TRUAPI_DEV`
in the debug flow; `CI_FIREBASE_GROUP`, `CI_FIREBASE_GROUP_VANILLA`,
`CI_FIREBASE_GROUP_SAFETYNET` in the nightly flow; `dev-team` inline in the release flow).

## Flows

### Pull request validation

**Trigger:** every pull request and merge-group run, except PRs from `release-*` branches
(those are built by the release flow instead).

- [`pr.yml`](workflows/pr.yml) — unit tests (`testGpDebugUnitTest`, `testGpReleaseUnitTest`,
  `testDebugUnitTest`, `testReleaseUnitTest`) and an `assembleGpDebug` build, in parallel jobs.
- [`detekt.yaml`](workflows/detekt.yaml) — static analysis.
- [`instrumental_tests.yaml`](workflows/instrumental_tests.yaml) — instrumentation tests,
  run only when the PR carries the `run-instrumental-tests` label.

### Development build distribution

**Trigger:** a PR merged into `main` or `truapi-dev`, or manual dispatch.
**Workflow:** [`firebase_debug_distribution.yml`](workflows/firebase_debug_distribution.yml)

1. The `resolve` job picks the target branch, the Firebase tester group for it and the
   release notes; closed-but-unmerged PRs and `release-*` PRs stop here.
2. Build number `10000 + github.run_number` is written into `Versions.kt` for the build.
3. `assembleGpDebug` with the dev keystore.
4. Upload to Firebase App Distribution (`ANDROID_FIREBASE_APP_ID`) and to object storage.

### Production release

**Trigger:** manual dispatch of the preparation workflow.

**Phase 1 — [`release_prepare.yml`](workflows/release_prepare.yml)**

1. Creates `release-<version>` from `source_ref` (default `main`).
2. Optionally bumps the marketing version (`no` / `patch` / `minor` / `major`) in
   `Versions.kt` and commits it.
3. Opens a PR `release-<version>` → `main` with the source ref embedded in the PR body.
4. Dispatches the distribution workflow for the release branch.

**Phase 2 — [`firebase_release_distribution.yml`](workflows/firebase_release_distribution.yml)**,
on that dispatch and on every update of the release PR

1. Security check: a `workflow_dispatch` must come from `github-actions[bot]` (via phase 1)
   and a `pull_request` must be authored by `github-actions[bot]`.
2. `CI_BUILD_ID` is `github.run_number`; the new build number is written back into
   `Versions.kt` on the release branch.
3. Unit tests, then `assembleGpRelease` with the release keystore.
4. Upload to Firebase App Distribution (`ANDROID_FIREBASE_RELEASE_APP_ID`, group
   `dev-team`) and to object storage; a comment with the build details is posted on the PR.

**Phase 3 — backport**, same workflow, when the release PR is merged

1. Reads `source_ref` from the merged PR body.
2. Opens a backport PR `release-<version>` → `source_ref` carrying the bumped version and
   build number plus any hotfixes made on the release branch.

### Nightly

**Trigger:** daily at 17:00 UTC, or manual dispatch.
**Workflow:** [`nightly_release.yaml`](workflows/nightly_release.yaml)

Skipped when nothing was merged since the previous nightly. Otherwise it builds
`gpNightly`, `vanillaNightly`, `gpSafetynet` and a `gpDebug` preview, uploads the three
nightly variants to Firebase App Distribution with their groups, uploads the APKs to
object storage, triggers an Allure TestOps launch, creates or replaces the GitHub
release `v<version>-<build>` with all four APKs attached, and posts a Matrix notification.

### Monthly PR summary

[`collect_prs_summary.yml`](workflows/collect_prs_summary.yml) runs on the 1st of each
month (or on demand with a `days` input) and reuses
`novasamatech/github-actions/.github/workflows/pr-summary-report.yml` to produce a report
of merged PRs.

## Version and build number

Both live in `build-logic/convention/src/main/kotlin/Versions.kt`.

- **Marketing version** (`DefaultVersionName`) changes only through `release_prepare.yml`
  or by hand.
- **Build number**: debug builds use `10000 + github.run_number` for the run and do not
  commit it; release builds write the new value into `DefaultVersionCode` on the release
  branch; nightly builds read the committed value.

Scripts in [`scripts/`](scripts/):

- `read_versions.py` — prints the current version and build number.
- `update_marketing_version.py` — sets `DefaultVersionName`.
- `update_build_number.py` — sets or increments `DefaultVersionCode`.

## Build artifacts storage

The debug, release and nightly flows upload their APKs to an S3-compatible bucket owned by
the maintainers. The bucket and region are `env` constants in each workflow (`S3_BUCKET`,
`S3_REGION` in the debug and release flows, inline `s3_bucket` / `s3_region` in the nightly
flow) and the credentials are `SCW_ACCESS_KEY` / `SCW_SECRET_KEY`. A fork points these at
its own bucket or deletes the upload steps.

| Flow | Versioned path | Static path (latest) |
|------|----------------|----------------------|
| Debug | `/android/debug/polkadot-app-{version}-{build}.apk` | `/android/debug/polkadot-app.apk` |
| Release | `/android/releases/polkadot-app-{version}-{build}.apk` | `/android/releases/polkadot-app.apk` |
| Nightly `gp` | `/android/nightly/polkadot-app-{version}-{build}.apk` | `/android/nightly/polkadot-app.apk` |
| Nightly `vanilla` | `/android/nightly/polkadot-app-vanilla-{version}-{build}.apk` | `/android/nightly/polkadot-app-vanilla.apk` |

## Composite actions

- [`actions/install`](actions/install/action.yaml) — JDK 21, Android SDK, Node 24,
  Python 3.13, GitHub CLI, NDK r29, Clang 21, Rust with the Android targets.
- [`actions/prepare-android-build`](actions/prepare-android-build/action.yaml) — decodes
  `google-services.json` and the requested keystore from their base64 secrets.
- [`actions/setup-clang`](actions/setup-clang/action.yaml) — installs the requested Clang.
- [`actions/upload-to-firebase`](actions/upload-to-firebase/action.yaml) — App Distribution upload.
