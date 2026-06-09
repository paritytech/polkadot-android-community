# Deployment Guide

This document explains **how to build, sign and publish** the Polkadot Android
Community application, and **which environment variables / secrets** you need to
configure.

It is intentionally **generic**. This repository does **not** ship a concrete
release pipeline. Instead, this guide
describes the *building blocks* so that anyone (the original team, a fork, or a
community deployment) can wire up their own publishing flow with their own
credentials.

> **Scope:** this is a manual / reference guide, not an automated deployment. Where
> a CI step is mentioned it is described as an option, not provided as a ready-to-run
> workflow.

---

## 1. How publishing works (overview)

A release is just a **signed APK/AAB** of a chosen build variant, delivered through
one or more channels. The app is built with Gradle, has native Rust components
(built via the NDK), and reads all credentials from `local.properties` **or**
environment variables (see §5). The typical flow is:

1. Provision the toolchain (§2).
2. Provide a signing keystore (§3) and a Firebase `google-services.json` (§4).
3. Set the required environment variables / secrets (§5).
4. Build the variant you want to ship (§6).
5. Deliver the artifact through a channel of your choice (§7):
   Google Play, Firebase App Distribution, GitHub Releases, object storage, or a
   direct APK link.

---

## 2. Toolchain / prerequisites

These versions are what the project is known to build with (they mirror the
previously-used CI environment):

| Tool            | Version                | Notes                                             |
|-----------------|------------------------|---------------------------------------------------|
| JDK             | 21 (Temurin)           | Required by AGP / Kotlin                           |
| Android SDK     | latest                 | Plus build-tools matching the project             |
| Android NDK     | r29                    | Needed for the native Rust bindings               |
| Clang           | 21                     | Native toolchain                                  |
| Rust            | stable                 | + targets below                                   |
| `cargo-ndk`     | latest                 | `cargo install cargo-ndk`                         |
| Node.js         | 24                     | Build helpers                                     |
| Python          | 3.13                   | Version-management scripts (§6)                   |

Rust targets:

```bash
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add x86_64-linux-android
rustup target add i686-linux-android
```

Point Gradle at the NDK by adding to `local.properties`:

```properties
ndk.dir=/path/to/android-ndk-r29
sdk.dir=/path/to/android-sdk
```

See `.github/README.md` for the local developer bootstrap (`developer-tools/setup.sh`).

---

## 3. Signing

The app defines two signing configs in `app/build.gradle.kts`:

- **`dev`** — used by the `debug` and `nightly` build types.
- **`release`** — used by the `release` build type.

### 3.1 Generate a keystore

```bash
keytool -genkeypair -v \
  -keystore release_key.jks \
  -alias my-release-key \
  -keyalg RSA -keysize 2048 -validity 10000
```

### 3.2 Where the keystore file lives

By default the build looks for the files relative to the repo root:

- dev: `develop_key.jks`
- release: `release_key.jks`

You can override the path with `DEV_KEYSTORE_FILE` / `RELEASE_KEYSTORE_FILE`
(see §5). In CI it is common to store the keystore base64-encoded in a secret and
decode it before the build:

```bash
echo "$RELEASE_KEYSTORE_BASE64" | base64 --decode > release_key.jks
```

### 3.3 Passwords / alias

Provided via the signing environment variables in §5
(`*_KEYSTORE_PASS`, `*_KEYSTORE_KEY_ALIAS`, `*_KEYSTORE_KEY_PASS`).

---

## 4. `google-services.json` (Firebase)

The app applies the Google Services + Firebase Crashlytics/Analytics plugins, so a
`google-services.json` must be present for each built variant source set:

```
app/src/debug/google-services.json
app/src/nightly/google-services.json
app/src/release/google-services.json
```

Obtain it from **your own** Firebase project (Project settings → Your apps →
Android app → download `google-services.json`). In CI it is typically stored
base64-encoded and decoded into the three locations before the build.

---

## 5. Environment variables / secrets reference

All build-time secrets are read by `Properties.readSecret(...)` /
`readSecretOrNull(...)` in `buildSrc/src/main/kotlin/Secrets.kt`, with this lookup
order:

1. a key in **`local.properties`**, then
2. an **environment variable** of the same name.

`readSecret` **throws** if the value is missing (required secret); `readSecretOrNull`
returns `null` and lets the build fall back to a default (optional / org-overridable).

### 5.1 Signing (required to build a signed variant)

| Variable                   | Used by                   | Required | Description                                  |
|----------------------------|---------------------------|----------|----------------------------------------------|
| `CI_KEYSTORE_PASS`         | `app` dev signingConfig   | yes (dev)| Store password for the dev keystore          |
| `CI_KEYSTORE_KEY_ALIAS`    | `app` dev signingConfig   | yes (dev)| Key alias for the dev keystore               |
| `CI_KEYSTORE_KEY_PASS`     | `app` dev signingConfig   | yes (dev)| Key password for the dev keystore            |
| `RELEASE_KEYSTORE_PASS`    | `app` release signingConfig| yes (rel)| Store password for the release keystore      |
| `RELEASE_KEYSTORE_KEY_ALIAS`| `app` release signingConfig| yes (rel)| Key alias for the release keystore          |
| `RELEASE_KEYSTORE_KEY_PASS`| `app` release signingConfig| yes (rel)| Key password for the release keystore        |
| `DEV_KEYSTORE_FILE`        | `app` dev signingConfig   | no       | Override path to dev keystore (default `../develop_key.jks`) |
| `RELEASE_KEYSTORE_FILE`    | `app` release signingConfig| no      | Override path to release keystore (default `../release_key.jks`) |

> Build types: `debug`/`nightly` use the **dev** config, `release` uses the
> **release** config. To assemble only debug/nightly you only need the dev secrets.

### 5.2 App API keys (consumed by the build via `buildConfigField`)

| Variable           | Used by (module)                          | Required | Description                          |
|--------------------|-------------------------------------------|----------|--------------------------------------|
| `GOOGLE_OAUTH_ID`  | `tools/auth/impl`                         | yes*     | Google OAuth client id (Sign-In)     |
| `GOOGLE_PROJECT_ID`| `tools/integrity/impl`                    | yes*     | Google Cloud project id (Play Integrity) |
| `W3S_AUTH_KEY`     | `feature/web3summit/impl`                 | yes*     | Web3 Summit auth keypair seed        |

`*` These are read with the throwing `readSecret`, so a build of the modules that
reference them **fails if absent**. For a fork that does not use a given
integration, you can supply a dummy value or adjust the corresponding module's
`build.gradle.kts`.

#### 5.2.1 App endpoints / values (optional — placeholder defaults)

These are also injected via `buildConfigField`, but read with the **non-throwing**
`readSecretOrNull` and fall back to a harmless placeholder when unset, so the build
still succeeds without them (the corresponding feature simply uses the placeholder).
Set them in `local.properties` / CI to point at your own infrastructure.

| Variable                    | Used by (module)            | Required | Default (placeholder)                  | Description                                                                 |
|-----------------------------|-----------------------------|----------|----------------------------------------|-----------------------------------------------------------------------------|
| `REFERRAL_WEB_HOST`         | `feature/become-citizen/impl` | no     | `referral.example.com`                 | Host of the web app that backs referral (`https`) deeplinks                 |
| `GAME_RESULTS_FALLBACK_URL` | `feature/videogame/impl`    | no       | `https://example.com/`                 | Last-resort URL for the game-results webview (after DotNs + Remote Config)   |
| `NIGHTLY_FUNDING_MNEMONIC`  | `feature/transactions/impl` | no       | well-known Substrate dev seed (`bottom drive …`) | Mnemonic of the funding account used to top up accounts on nightly/testnet |
| `LOG_COLLECTION_EMAIL`      | `app`                       | no       | `logs@example.com`                     | Recipient address for the in-app "collect logs" debug share action          |

> `NIGHTLY_FUNDING_MNEMONIC` controls a funding account — keep the real value in a
> secret store / `local.properties`, never commit it. The placeholder is the public
> Substrate development seed and only seeds a throw-away dev account.

### 5.3 Sentry (crash/error reporting)

| Variable             | Used by               | Required | Description                                            |
|----------------------|-----------------------|----------|--------------------------------------------------------|
| `SENTRY_ORG`         | `app/build.gradle.kts`| no       | Sentry org slug. Default: `your-sentry-org` (placeholder — set to your org) |
| `SENTRY_PROJECT`     | `app/build.gradle.kts`| no       | Sentry project slug. Default: `your-sentry-project` (placeholder — set to your project) |
| `SENTRY_AUTH_TOKEN`  | Sentry Gradle plugin  | no       | Token for source upload. Only used for `debug`/`nightly` (release variant is ignored). Omit to skip upload. |
| `SENTRY_DSN`         | `app` debug/nightly manifests (`${sentryDsn}` manifest placeholder) | no | DSN crashes/errors are reported to. Default: empty (Sentry reporting disabled). Only the `debug`/`nightly` manifests reference it; `release` has no DSN meta-data. |

Override `SENTRY_ORG` / `SENTRY_PROJECT` to point crash reporting at **your** Sentry,
and set `SENTRY_DSN` to the project's DSN (otherwise crash reporting stays disabled).

### 5.4 Build

| Variable      | Used by                                   | Required | Description                                   |
|---------------|-------------------------------------------|----------|-----------------------------------------------|
| `CI_BUILD_ID` | `buildSrc/.../Versions.kt` (`versionCode`)| no       | Integer `versionCode`. Defaults to `19` if unset. |

The marketing `versionName` is stored in `buildSrc/src/main/kotlin/Versions.kt`
(`DefaultVersionName`) and managed by the scripts in §6.

### 5.5 Legacy / not currently consumed by the build

The previous CI fetched the following secrets, but **no source or build file in this
repository currently reads them**. They are listed for completeness; treat them as
optional and only wire them up if you re-introduce the corresponding integration:

`RAISE_CLIENT_ID_DEBUG`, `RAISE_SECRET_DEBUG`, `RAISE_CLIENT_ID_PROD`,
`RAISE_SECRET_PROD`, `MERCURYO_PRODUCTION_SECRET`, `POSTHOG_HOST`,
`POSTHOG_API_KEY`, `POSTHOG_API_KEY_DEV`, `POSTHOG_API_KEY_PROD`.

### 5.6 Publishing/infra secrets (channel-dependent — see §7)

These are **not** consumed by the Gradle build; you only need them in the publishing
step of whatever channel you choose:

| Variable                          | Channel                       | Description                                |
|-----------------------------------|-------------------------------|--------------------------------------------|
| `FIREBASE_GOOGLE_SERVICE_ACCOUNT` | Firebase App Distribution     | Service-account JSON (content)             |
| Firebase **App ID**               | Firebase App Distribution     | Target Firebase app id                     |
| Play service-account JSON         | Google Play                   | For Play Developer API uploads             |
| `GITHUB_TOKEN`                    | GitHub Releases               | Provided automatically in GitHub Actions   |

---

## 6. Building

Product flavors: `gp` (Google services) and `vanilla`. Build types: `debug`,
`nightly`, `release`.

```bash
# Debug (dev signing)
./gradlew assembleGpDebug

# Nightly (dev signing)
./gradlew assembleGpNightly

# Release (release signing) — requires the release keystore + secrets
./gradlew assembleGpRelease
```

Outputs land in `app/build/outputs/apk/<flavor>/<buildType>/`.

### Version management

The marketing version and the build number are defined in
`buildSrc/src/main/kotlin/Versions.kt`:

```kotlin
private const val DefaultVersionName = "1.0.0"   // versionName
private const val DefaultVersionCode = 19        // versionCode (fallback)
```

- **versionName** — edit `DefaultVersionName` directly when cutting a release.
- **versionCode** — edit `DefaultVersionCode`, or override per build (without editing
  the file) via the `CI_BUILD_ID` environment variable (see §5.4), which takes
  precedence when set.

---

## 7. Publishing channels (pick what you need)

The repository no longer contains these pipelines. Below is **how** each channel is
typically wired so you can implement it on your own infrastructure.

### Google Play

- Upload `app-gp-release.aab`/`.apk` to the Play Console manually, **or**
- Use the Play Developer API (e.g. the `r0adkll/upload-google-play` GitHub Action or
  `fastlane supply`) with a Play service-account JSON.

### Firebase App Distribution (internal/QA builds)

- Upload the signed APK with a Firebase service account
  (`FIREBASE_GOOGLE_SERVICE_ACCOUNT`) and the target **App ID**, e.g. via the
  Firebase CLI (`firebase appdistribution:distribute`) or an equivalent GitHub Action.

### GitHub Releases

- Tag the commit and attach the APK:

```bash
gh release create "v$VERSION" \
  --title "$VERSION" \
  --notes "release notes" \
  app/build/outputs/apk/gp/release/app-gp-release.apk
```

`gh` uses the automatically-provided `GITHUB_TOKEN` in Actions.

### Object storage / direct APK

- Upload the APK to any bucket/CDN (S3-compatible, GCS, etc.) and publish the link.

---

## 8. (Optional) Bringing back CI on your own infrastructure

The removed workflows depended on private infrastructure. To run build/test/publish
in GitHub Actions on standard runners, store the secrets from §5 as **GitHub
Actions secrets** and reference them as environment variables — the Gradle build
already reads them via `readSecret`/`readSecretOrNull`, so no code changes are
needed. A minimal sketch:

```yaml
name: Build
on: [pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    env:
      CI_KEYSTORE_PASS: ${{ secrets.CI_KEYSTORE_PASS }}
      CI_KEYSTORE_KEY_ALIAS: ${{ secrets.CI_KEYSTORE_KEY_ALIAS }}
      CI_KEYSTORE_KEY_PASS: ${{ secrets.CI_KEYSTORE_KEY_PASS }}
      GOOGLE_OAUTH_ID: ${{ secrets.GOOGLE_OAUTH_ID }}
      INTERCOM_API_KEY: ${{ secrets.INTERCOM_API_KEY }}
      INTERCOM_APP_ID: ${{ secrets.INTERCOM_APP_ID }}
      GOOGLE_PROJECT_ID: ${{ secrets.GOOGLE_PROJECT_ID }}
      W3S_AUTH_KEY: ${{ secrets.W3S_AUTH_KEY }}
      SENTRY_ORG: ${{ secrets.SENTRY_ORG }}
      SENTRY_PROJECT: ${{ secrets.SENTRY_PROJECT }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      # ... install Android SDK, NDK r29, Rust + targets, Clang, Node, Python (see §2)
      - run: |
          echo "${{ secrets.DEV_KEYSTORE_BASE64 }}" | base64 --decode > develop_key.jks
          echo "${{ secrets.GOOGLE_SERVICES_JSON_BASE64 }}" | base64 --decode > app/src/debug/google-services.json
      - run: ./gradlew assembleGpDebug --no-daemon --stacktrace
```

Add a publishing job (§7) only on the events/branches you want to ship from.
