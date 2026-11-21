# Device testing

Use an Android phone (API 26+) with wired/USB and Bluetooth headsets. Start at a
low, comfortable volume. Stop immediately if sound is unpleasant. Automated
checks and emulator runs do not validate acoustic loudness, latency or clarity.

```sh
./gradlew :app:assemblePatientDebug
adb install -r app/build/outputs/apk/patient/debug/app-patient-debug.apk
```

## Suno family link (Phase 2, not yet checked on a device)

Needs two Android 10+ phones: the patient app on one, Suno Companion on the other.

```sh
./gradlew assemblePatientDebug assembleCompanionDebug
adb -s <patient> install -r app/build/outputs/apk/patient/debug/app-patient-debug.apk
adb -s <companion> install -r app/build/outputs/apk/companion/debug/app-companion-debug.apk
```

- Patient: Helper mode → Settings → Show join code; grant Nearby devices (or location on Android 10–12) and Bluetooth.
  The status line should say Ready with Wi-Fi Direct and Bluetooth.
- Companion: Family → enter a name → Scan join code. The patient's phone shows "<name> wants to talk to you". Check
  Allow (connected, transport shown) and Don't allow (companion shows refused).
- With the patient listening, tap Talk. Check the voice in the patient's earphones, the talking banner, the Now
  hearing card, and that the room is turned down while talking. Measure the delay.
- Repeat with the patient phone connected to home Wi-Fi, and with Wi-Fi Direct unavailable so Bluetooth is used
  (Opus). Note any phone where creating or joining the group fails.
- Lock both screens while talking; voice should continue. Stop from the companion's notification.
- Remove the companion in Helper settings; it should be disconnected and asked again next time.
- Two companions at once; leave and rejoin; patient stops listening (companion shows not connected).
- Battery and heat on the patient phone after 30 minutes of hosting and listening.

## Suno patient app (Phase 1, not yet checked on a device)

Screenshots rendered by `PatientScreenshotTest` (Robolectric) are in `docs/images/suno/`. On a phone, check:

- First run: welcome, set a 4-digit helper PIN, EarCast onboarding, then Lock returns to the patient Home.
- The Listen button starts and stops listening; the halo appears only while listening. TalkBack reads the state.
- Volume − / + changes loudness live and stops at the helper's highest volume; Night stops at step 3.
- Changing the place while listening restarts listening within a second or two with the new noise settings.
- Wrong PIN shows a message and clears the dots; the right PIN opens Helper mode.
- Helper → Settings → Patient screen: home level, pictures/words, colours and highest volume change Home at once.
- "Start when earphones go in": with Suno open, plugging in earphones starts listening. It does not start from the
  background (Android does not allow it).
- Hindi: set the app language to हिन्दी in Android settings; the patient screens switch, other screens stay English.
- 200% font size: Home still works by scrolling, nothing is cut off.

## Suno Phase 0 changes (not yet checked on a device)

- **Feedback guard at the live block size.** With earphones close to the phone microphone and gain raised slowly,
  a whistle between about 600 Hz and 3 kHz should be pulled down within a fraction of a second. Normal speech, a
  low voice and music should not be ducked. Record the pitch of any whistle that is not caught.
- **Short interruptions.** Play a notification sound, then a navigation prompt or voice assistant reply, while
  listening. Listening should continue through the notification, fade out during the prompt and fade back in
  afterwards without a click. A phone call or another app's long playback should still end the session quietly.
- **Headphones only.** Starting with no headphones connected must fail with a message, never play through the
  speaker. Unplugging during listening must stop sound.
- **No profile yet.** On a fresh install with no saved profile, listening should start with the Mild ready-made
  profile.
- **Volume cap.** The master volume slider tops out at +20 dB.
- **Still to measure:** capture backlog after a scheduling stall (latency should not keep growing over a long
  session).

## Hearing checks and profiles

- Confirm safety terms and the uncalibrated-estimate notice appear before testing.
- Check left/right routing with headphones, clean tone ramps and volume control.
- Confirm responses change the tone level and advance through both ears.
- Stop/mute mid-tone; output must stop immediately.
- Complete a check, save/select a profile, restart the app and verify persistence.
- Test manual entry, tone preview, profile deletion and the results share preview.

## Live listening

- Compare phone and headset input with the microphones physically separated;
  verify the selected microphone captures the sound.
- Start/stop repeatedly and cancel while connecting. Deny/revoke microphone
  permission, interrupt with a call, and disconnect the headset. Expect silence,
  released routing and a cleared notification; never speaker fallback.
- Test wired/USB, classic Bluetooth on Android 8–11 and 12+, and LE Audio where
  available. Check mono reporting on SCO and per-ear separation on stereo routes.
- Compare processing engines at the same comfortable perceived volume. Check
  quiet words, consonants, distortion, feedback and sudden level changes.
- Measure acoustic delay with external equipment. Check extended playback for
  dropouts, heat and battery drain; PCM rate and block size are not latency measurements.
- Test the saved microphone/settings through the Quick Settings tile.

See [headset routing](HEADSET_MICROPHONE.md) for connection-specific checks and
[speech processing](SPEECH_UNDERSTANDING.md) for engine comparisons.

## Background listening

- Fresh install: battery setup appears once after safety acceptance. Not now and
  Back dismiss it; restarting must not repeat it.
- Open battery settings, change optimization/restriction, then return. The app
  must report the actual OS state, including after cancelling a change.
- Verify unavailable settings destinations fall back without crashing.
- Run assist for an hour with the screen locked and while using other apps.
  Test an OEM with aggressive battery management. Notification Stop must release
  microphone access and the wake lock.

## Media and interface

- Compare Media sound Off, Balanced and Speech clarity with the same track.
  Check boost, distortion, unsupported players and restoration after assist or tones.
- Check sound preview switching for clicks and confirm playback stops on navigation.
- Exercise large fonts, high contrast, dark mode, tablets, system navigation
  insets and the fixed Stop/mute controls.
- Repeat critical flows in the minified release build on API 26, a current Android
  version, and a 16 KB page-size device where available.

Record device/OS/headset, build version, settings, reproduction steps and results.
Report unsafe loudness through [SECURITY.md](../SECURITY.md). Complete
[calibration](CALIBRATION.md) before making loudness claims.
