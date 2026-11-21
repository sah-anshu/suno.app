# Release and distribution

Suno ships as three apps from one code base: **Suno** (`au.remotiq.suno`, patient), **Suno Companion**
(`au.remotiq.suno.companion`) and **Suno TV** (`au.remotiq.suno.tv`). All three share one version.

Before any public release, complete the [device tests](DEVICE_TESTING.md) on real phones, earphones, hearing aids and
TVs, the [comfort calibration](CALIBRATION.md), and the [safety rules](SAFETY.md). Passing unit tests and lint does not
show that the sound is safe, that the radios work, or that calls and SOS work. Version 0.1.0 has **not** been checked on
a device: treat it as a test build for people who know they are testing.

## Checks before a release

```sh
./gradlew ktlintCheck detekt test testPatientDebugUnitTest
./gradlew :app:lintPatientRelease :app:lintCompanionRelease :app:lintTvRelease -PsunoLocalRelease=true
```

Lint on the release variants must pass. Missing Hindi in EarCast's helper screens is a warning by design.

## Local and GitHub release APKs

```sh
just build-prod            # signed APKs of all three apps, one per CPU type (about 75-90 MB each)
just build-prod-universal  # one APK per app with all CPU types (about 210 MB each)
```

Outputs are in `app/build/outputs/apk/<app>/release/`, for example `app-patient-arm64-v8a-release.apk`. The task
verifies every signature. It uses `keystore.properties` when present; otherwise the local debug key (for testing only:
an APK signed with the debug key cannot be updated by one signed with the release key).

Most phones need `arm64-v8a`; check with `adb shell getprop ro.product.cpu.abilist`. TVs are often `armeabi-v7a`.

For a GitHub release: build with the release key, attach the 12 APKs (or the three universal ones), paste the section
of [CHANGELOG.md](../CHANGELOG.md), and link the source tag. Pushing a `v*` tag runs `.github/workflows/release.yml`,
which builds signed APKs when the signing secrets are set (see below) and attaches them to a draft release.

## Signing

Generate one upload key and keep it backed up; never commit keys or passwords:

```sh
keytool -genkey -v -keystore suno-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias suno
```

Create gitignored `keystore.properties` in the repository root:

```properties
storeFile=/absolute/path/to/suno-release.jks
storePassword=YOUR_PASSWORD
keyAlias=suno
keyPassword=YOUR_PASSWORD
```

For the GitHub release workflow, add repository secrets `SUNO_KEYSTORE_BASE64` (the `.jks` file in base64),
`SUNO_KEYSTORE_PASSWORD`, `SUNO_KEY_ALIAS` and `SUNO_KEY_PASSWORD`. Without them the workflow builds test APKs signed
with a throwaway debug key and says so in the release notes.

All three apps must always be signed with the same key: the family link does not depend on it, but updates do.

## Version

Raise `versionCode` and `versionName` in `app/build.gradle.kts` for every release, add a section to `CHANGELOG.md` and
a `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for each app, then tag `v<versionName>`.

## Store listings

| App | Listing text and screenshots |
|---|---|
| Suno (patient) | `fastlane/metadata/android/` (en-US, hi-IN) |
| Suno Companion | `app/src/companion/fastlane/metadata/android/` |
| Suno TV | `app/src/tv/fastlane/metadata/android/` |

F-Droid reads the flavour folders (`app/src/<flavour>/fastlane`) for flavour builds. Screenshots come from the
Robolectric screenshot tests (`docs/images/suno`). Never claim calibrated hearing tests, treatment, guaranteed safety,
or that SOS replaces emergency services.

## Channels

- **GitHub Releases** (first): signed APKs for testers. Recommended for 0.x.
- **F-Droid**: a natural home for a GPL app without Google services. Open points: EarCast's prebuilt sherpa-onnx / ONNX
  Runtime archive must be built from source (F-Droid policy), and `SEND_SMS`, the phone-app role and the background
  link need clear descriptions. Submit a recipe to `fdroiddata` with one build entry per flavour.
- **Google Play**: see [GOOGLE_PLAY.md](GOOGLE_PLAY.md). A Play build must drop `SEND_SMS` (SOS then opens the
  messaging app) unless Play grants an exception, and needs declarations for the phone-app role, foreground services
  and full-screen notifications.

## Source for recipients

Suno is GPL-3.0-only. Every binary release must point to the exact source tag. Keep `LICENSE`, `LICENSE-EarCast.txt`
and [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) with the source.
