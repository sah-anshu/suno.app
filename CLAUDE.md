# Suno: project guide

Suno is an offline Android hearing-assist system for elderly people and for children and adults with neurological or
cognitive disabilities. It is a fork of [EarCast](https://github.com/octavian-negru/ear-cast) and is licensed
GPL-3.0-only.

**Read [arch.md](arch.md) first.** It holds the architecture, the decisions and their state (Decided / Proposed /
To verify), and the build phases. Update it, including its decision log, whenever a decision changes.

## Build and check

The Android SDK path goes in `local.properties` (`sdk.dir=...`), which is gitignored. Gradle provisions JDK 27 itself.

```bash
./gradlew ktlintCheck detekt test testPatientDebugUnitTest
./gradlew assemblePatientDebug assembleCompanionDebug assembleTvDebug
```

Behind a rate-limited proxy, Maven Central can answer 429 during the first dependency download. Rerun with
`--max-workers=1`; Gradle keeps what it already downloaded.

## Layout

| Path | What |
|---|---|
| `foundation` | Units and `AudioLimits` (all output limits live here) |
| `sound-profile` | Audiogram, tone check, fitting, ready-made profiles |
| `audio-engine` | Streams, routing, DSP chain, native noise reduction (`src/main/cpp`), media EQ |
| `local-storage` | DataStore profiles and preferences |
| `app` | Flavours `patient` (`au.remotiq.suno`), `companion` (`.companion`), `tv` (`.tv`); shared EarCast screens in `src/main` |
| `audio-quality` | Offline audio regression bench (not an app module) |
| `docs/` | Safety, fitting, calibration, device testing, release |

Kotlin packages are `au.remotiq.suno.*`.

## Rules that are never broken

- The limiter is the last processing stage. Nothing reaches an output without it. Limits live only in `AudioLimits`.
- The patient's own active audiogram is applied on the patient's phone to every source. Senders send clean audio.
  Hearing-aid output gets no profile gain.
- Amplified sound never plays through the phone speaker. Fail quiet, never loud.
- The patient's microphone audio never leaves the patient's phone, except in SOS and calls.
- Offline only: no analytics, no accounts, no uploads. The device-to-device link works without Google services by
  default; Google Nearby is an optional extra.
- Patient-facing screens follow the accessibility rules in arch.md section 11.3: large targets, never colour alone,
  Okabe–Ito palette, no timeouts.
- User-facing copy never says diagnose, treat or medical, except to say Suno is not a medical device.

## Working agreement

- Build in the phases listed in arch.md section 15. Commit small, working steps with Conventional Commit messages.
- Run the checks above before every commit. Do not skip or weaken a test to make it pass.
- Keep vendored code under `audio-engine/src/main/cpp/vendor`, model files and their checksums unchanged.
- Keep upstream copyright and third-party notices. New files carry `// SPDX-License-Identifier: GPL-3.0-only`.
- Be explicit about what is unverified: anything about real audio latency, feedback, Bluetooth, calls or TVs needs a
  physical device and goes into `docs/DEVICE_TESTING.md`.
