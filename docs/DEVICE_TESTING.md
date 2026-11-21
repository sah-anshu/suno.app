# Device testing

Use an Android phone (API 26+) with wired/USB and Bluetooth headsets. Start at a
low, comfortable volume. Stop immediately if sound is unpleasant. Automated
checks and emulator runs do not validate acoustic loudness, latency or clarity.

```sh
./gradlew :app:assemblePatientDebug
adb install -r app/build/outputs/apk/patient/debug/app-patient-debug.apk
```

## Suno phone app (Phase 5, not yet checked on a device)

Needs the patient phone with a SIM and a second phone to call from. Try a Pixel, a Samsung and a Xiaomi if possible.

- Helper mode → Settings → People and calls: Make Suno the phone app (system prompt), add people from contacts and by
  typing, reorder, remove. Allow contacts and calling.
- Home: Call <first person> calls at once; People tiles call with one tap; Type a number dials. A `tel:` link in a
  browser or message opens Suno's keypad.
- Incoming call with the phone locked and unlocked: the Suno screen appears, the phone rings, and Answer and Decline work.
  An unknown number shows the warning. Note OEMs where only a notification appears.
- In a call: switch between Phone, Speaker, wired earphones, Bluetooth earphones and a hearing aid (ASHA / LE Audio);
  volume buttons; mute (the other side hears silence); End call. A second call while in a call is declined.
- Contacts only: a call from an unknown number does not ring and is in the call log. During an SOS the same number
  rings.
- Auto-answer: a call from a person in People is answered after about three rings, on earphones if connected, else
  the speaker.
- SOS escalation (use a test number as the emergency number, **never real 112**): helper does not answer → after
  about 45 s the test emergency number is called; helper declines → called at once; helper answers → not called;
  helper's voicemail → note what happens. Turn the setting off and check that no call is made.
- Without the role: calls go through the phone's own app and SOS shows the emergency button only.
- Listening stops when a call starts and does not play through the speaker.

## Suno SOS and mesh (Phase 4, not yet checked on a device)

Needs the patient phone with a SIM, two companion phones and, if possible, a Suno TV. Use a family member's number as
the helper and as the emergency number for testing; **do not test with real 112**.

- Helper mode → Settings → SOS: enter the helper and text numbers, set the emergency number to a test number, and tap
  Allow calls, texts and location.
- Tap SOS → No, go back: nothing is sent. Tap SOS → Yes, get help:
  - every connected companion shows the full-screen alert, including with its screen locked and Suno Companion closed;
  - the helper's phone rings;
  - texts arrive with the place (check accuracy indoors and outdoors, with mobile data off);
  - the TV shows the banner.
- Without SMS permission: the SOS screen shows Send text with my place, which opens the messaging app prefilled.
- The emergency button opens the phone app with the number filled in.
- Alarm with wired and Bluetooth earphones connected: sound comes only from the phone speaker, never in the
  earphones. On a phone where the speaker cannot be chosen, the button stays off.
- Companion: I'm on my way appears on the patient's SOS screen and on the other companion. I'm safe now: companions
  show safe now, the notification changes, the alarm stops.
- Late joiner: with the patient not listening, a companion out of range comes in range during an SOS. It should get
  the alert within about a minute.
- Background link: leave Suno Companion closed for an hour; note battery use and whether the notification stays.
- Relay: companion A near the patient, companion B out of the patient's range but near A. B shows Through another
  family phone, can talk, and gets the SOS. Note the range and the delay.

## Suno TV sound (Phase 3, not yet checked on a device)

Needs the two phones above plus, where available, an Android TV / Google TV (Android 10+) and a Fire TV.

```sh
./gradlew assembleTvDebug
adb -s <tv> install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk
```

- **Cable route (any TV).** TV headphone or optical output → USB-C audio adapter → companion phone. Companion: Family →
  Share sound → TV by cable. Check TV sound in the patient's earphones, the TV chip in Now hearing, the room turned down,
  and that a family member talking turns the TV down. Unplug the adapter: sharing should stop quietly.
- **Microphone near the TV** and **Sound playing on this phone** (accept the capture prompt; check a music app and a
  video app, and note apps that stay silent).
- **TV set-up.** Open Suno TV; it shows a set-up code. Companion (joined to the patient): Family → Set up a TV → scan.
  The companion says the TV is set up and reconnects to the patient; the patient's phone asks to allow the TV. Check
  Allow and Don't allow (TV shows refused and Show setup code again). Note how long the handover takes.
- **Remote.** Every TV screen works with arrows and OK only; the main button has focus on arrival and shows a thick
  ring. Text readable from 3 m.
- **Playback capture on the TV.** Start sharing TV sound → consent screen → sound reaches the patient. Check live TV,
  YouTube, Netflix/Prime (expected silent if protected) and the TV's own menus. Note TVs with no consent screen (Suno TV
  should show the cable-route message).
- **Fire TV.** Install the same APK by adb; repeat set-up and capture. Note the Fire OS version.
- Turn the TV off and on, then open Suno TV: it should reconnect to the patient without a new set-up. Patient stops listening: the
  TV shows Not connected with Try again.
- Delay between the TV picture and the sound in the earphones (lip sync), by cable and by playback capture.
- TV speakers keep their normal volume throughout.

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
