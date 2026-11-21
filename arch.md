# Suno architecture

Reference document for the Suno project. Update it when a decision changes, and add a line to the
[decision log](#16-decision-log).

- **Status (2026-11-21):** Phases 0–2 complete in code, **not yet run on a device**. The patient app opens Suno's own Home; Helper mode uses EarCast's screens behind a PIN; a companion can join by QR (Wi-Fi Direct or Bluetooth LE) and talk to the patient. Screenshots: `docs/images/suno/`.
- **Licence:** GPL-3.0-only ([section 13](#13-licensing)).
- **Baseline:** [EarCast](https://github.com/octavian-negru/ear-cast) at commit `0a2213b` (GPLv3), which Suno forks.
- **UI mockup (v10):** https://claude.ai/artifact/PSLPf47Vd5DeC63ac5v3xH (private to its owner until shared).
  People named in it (Dadi, Priya, Rahul, Nana) are examples.

Each item in this document carries one of three states:

| State | Meaning |
|---|---|
| **Decided** | Agreed with the project owner. |
| **Proposed** | Shown in the mockup or suggested, not yet confirmed. |
| **To verify** | Depends on a platform behaviour or measurement that has not been tested on real devices. |

---

## 1. What Suno is

Suno ("listen" in Hindi) is a native Android (Kotlin) hearing-assist system for elderly people and for children and
adults with neurological or cognitive disabilities. It works fully offline.

- It makes sound louder and clearer using the patient's audiogram.
- It is a local audio hub. TV sound and nearby people's phones stream **in**; processed sound goes **out** to wired
  earphones, Bluetooth earbuds, or ASHA / LE Audio hearing aids.
- It is also the patient's phone app, contacts and SOS button, so the patient needs only one simple screen.
- Family and carers use companion apps to talk to the patient, share sound, help remotely and receive SOS alerts.

Suno is a sound-amplification tool, **not a medical device**. User-facing copy never says diagnose, treat or medical.

## 2. Principles

1. **The patient's audiogram always applies.** Every sound that reaches a patient passes through that patient's
   active hearing profile on the patient's own phone ([section 6.1](#61-the-audiogram-rule)).
2. **Suno decides, the patient can override.** Place, sound source and start/stop are automatic by default. *(Proposed)*
3. **Offline only.** No internet, no account, no cloud, no recordings. Sound moves only between nearby devices.
4. **Dignity.** Nobody can listen through the patient's phone. The patient's microphone is never sent to another
   device, except in an SOS or a call the patient is part of.
5. **Fail quiet, never loud.** The output limiter is always the last processing stage. Any error stops sound.
6. **Patients see the simple layout only.** Everything complex sits behind the helper PIN or in the companion apps.
7. **Aligned with Google ASHA.** Hearing aids are a first-class output, and Suno never stacks its gain on a hearing
   aid's own fitting.

## 3. Apps: one codebase, three flavours

**Decided:** the companion apps are built as Android product flavours of one codebase, not as modes chosen at setup.

| Flavour | Application ID | Who uses it | Layout | Main features |
|---|---|---|---|---|
| `patient` | `au.remotiq.suno` | The person being helped, plus their helper behind a PIN | Suno simple layout; Helper mode uses EarCast tabs | Listen, hub for incoming streams, phone app and contacts, SOS, mesh owner, QR join approval |
| `companion` | `au.remotiq.suno.companion` | Family, carers, guests; also tablets | EarCast tabs plus a Family tab | Talk to the patient, Share my sound (playback or mic), remote help, SOS alerts, own hearing (optional) |
| `tv` | `au.remotiq.suno.tv` | Android TV / Google TV / Fire TV | 10-foot TV screen | Show a set-up QR that a companion scans, capture TV playback sound, stream to the patient's Suno, show SOS banner |

```kotlin
// app/build.gradle.kts (sketch)
android {
    flavorDimensions += "role"
    productFlavors {
        create("patient")   { dimension = "role" }
        create("companion") { dimension = "role"; applicationIdSuffix = ".companion" }
        create("tv")        { dimension = "role"; applicationIdSuffix = ".tv" }
    }
}
```

- Shared code lives in `app/src/main` and the library modules. Role-specific code lives in `app/src/patient`,
  `app/src/companion` and `app/src/tv`, each with its own manifest, launcher entry and permissions.
- Each flavour requests only the permissions its role needs. Only `patient` asks for the phone-app role, contacts
  and SMS, which keeps store review for `companion` and `tv` simple.
- A tablet sender is the `companion` flavour on a tablet. A "TV sender" phone connected by cable
  ([section 9](#9-tv-platforms)) is also the `companion` flavour.
- **Consequence:** the setup question "How will this phone be used?" and the Mode switch in Settings, both shown in
  mockup v10, are superseded. Each app opens directly in its role.
- **Decided:** the default builds contain no Google services. An optional `gms` build adds Nearby Connections
  ([section 8.4](#84-transports)); add it as a second flavour dimension when Phase 2 starts.

Build commands once the code exists:

```bash
./gradlew assemblePatientDebug assembleCompanionDebug assembleTvDebug
```

## 4. Baseline: what Suno takes from EarCast

EarCast is a GPLv3 Kotlin app (Compose, Hilt, coroutines, DataStore) with native noise reduction. It has 188 unit
tests and has **never been validated on real hardware** (its own docs say so).

### 4.1 Reused as-is

| EarCast part | Location in EarCast | Role in Suno |
|---|---|---|
| Live capture → process → playback engine | `audio-engine`: `AndroidStreamEngine`, `LiveAudioStreams` | The Listen path |
| Mic and output routing (wired, USB, SCO, LE Audio) | `LiveAudioRoute`, `RouteRules` | Earphone / hearing-aid detection, pause on unplug |
| Per-ear chain | `MonoListeningChain`, `StereoListeningChain` | Applies the audiogram, per ear |
| Profile EQ, 3-band compressor, presence filter | `ProfileEqualizer`, `BandCompressor`, `DynamicCompressor` | Core hearing processing |
| Noise reduction: RNNoise, DPDFNet8, SpeexDSP, Wiener | `audio-engine/src/main/cpp`, `*Bridge.kt` | All four kept **(Decided)** |
| ONNX runtime via sherpa-onnx | vendored in `audio-engine` | Kept for DPDFNet8; also the candidate engine for captions |
| Output limiter and limits | `PeakLimiter`, `foundation`: `AudioLimits` | Final safety stage |
| Tone hearing check, audiogram editor, half-gain fitting | `sound-profile`: `FrequencyGainCurve`, `ProfileFitting` | Helper → Profiles |
| Media sound EQ | `MediaSoundController`, `MediaCurvePlanner` | Kept in the first version **(Decided)** |
| Listening-time meter | `ListeningTracker` | Comfort / exposure reminder |
| Foreground service, notification, quick-settings tile | `LiveAudioService`, `LiveAudioTile` | Screen-off listening |
| Storage | `local-storage`: `PreferenceStorage`, `ProfileStorage` | Extended with Suno keys |
| EarCast's screens (Listen / Profiles / Settings) | `app/ui/*` | Helper mode and the companion app **(Decided)** |

### 4.2 Changed or fixed

Status after Phase 0: done, except two items deferred to Phase 1 because they need a real phone or are part of
the Hindi work (marked *deferred*).

| Issue found in the study | Change |
|---|---|
| `FeedbackSuppressor` normalises by full-block energy, so at the real 192-frame block it misses howls below about 2.5 kHz. Tests passed only because they used 1024-sample blocks. **Confirmed by a unit test:** a 1.5 kHz howl got no protection. | Fixed: per-lag overlap normalisation plus a pitch-detector rule (a lag counts only after the autocorrelation dips below zero). Tests at 192 frames: 600 Hz–3 kHz howls duck; noise, rumble and a voiced vowel stay open. Device check pending. |
| Master gain up to +40 dB on top of +40 dB of profile gain. | `MAX_MASTER_GAIN_CAP_DB` lowered to 20 dB. A helper-set lower cap comes with the patient layout. |
| No listening without a hearing test. | `ReadyMadeProfile` (Mild, Moderate, Child-safe); live listening falls back to Mild. |
| Any audio-focus loss, even "can duck", stops the session. | `FocusPolicy` + `FocusGate`: continue on can-duck, fade out and back in on transient loss, stop only on permanent loss. |
| Up to 100 ms capture backlog is never drained; latency unmeasured. | *Deferred:* measure on a device first, then drain. |
| About 26 English error strings hard-coded in `audio-engine`. | *Deferred to Phase 1:* error codes mapped to string resources (needed for Hindi). |
| `requireHeadphones` is never set for on-body listening. | Set for every live session. |
| Universal APK about 150 MB. | Ship an App Bundle with per-ABI splits (about 75–80 MB on arm64, estimated). |

### 4.3 Toolchain inherited

Gradle 9.8.0, AGP 9.4.1, Kotlin 2.4.20, JDK 27 for the Gradle daemon (Java 17 bytecode), compile SDK 37, target SDK 36,
min SDK 26, NDK 30, CMake 4.1.2, ktlint and detekt.

## 5. Module map

| Module | Origin | Kind | Responsibility |
|---|---|---|---|
| `foundation` | EarCast | Kotlin/JVM | Units, `AudioLimits` |
| `sound-profile` | EarCast | Kotlin/JVM | Audiogram, tone check, fitting |
| `audio-engine` | EarCast | Android + NDK | Streams, routing, DSP chain, noise reduction, media EQ |
| `local-storage` | EarCast | Android | Profiles and preferences (DataStore) |
| `audio-quality` | EarCast | Tooling | Offline audio regression bench |
| `audio-graph` | New (Phase 2) | Kotlin/JVM | `Resampler`, `JitterBuffer`, `RemoteAudioHub` (companions' voices), `VoiceMixer` (ducks the room while a person talks). `OutputPolicy` comes with hearing-aid mode |
| `link` | New (Phase 2) | Kotlin/JVM | `JoinCode` (QR payload), `LinkCrypto` (HKDF + AES-GCM), `LinkMessage`, `LinkHost` / `LinkClient`, `StreamChannel`, socket transport. `Sos` / `SosReply` messages, `LinkRelay` (two-hop mesh relay) |
| `link-android` | New (Phase 2) | Android | Default transports without Google services: `WifiDirectHost/Joiner`, `BluetoothHost/Joiner` (L2CAP, relay flag in the advert), `OpusCodec`, `LinkPermissions`, `LinkConnector` / `LinkHosting` / `RelayHosting` |
| `link-nearby` | New, optional | Android | Google Nearby Connections, only in the optional `gms` build |
| `sender-core` | New | Android | Playback capture and mic capture → Opus → `link`. Used by `companion` and `tv` |
| `phone` (package in `app`, built in Phase 5) | New | Android | Phone-app role, call screens, call routing, call screening, People. `patient` only |
| `sos` (package in `app`, built in Phase 4) | New | Android | `SosController`, `SosActions` (calls, texts, GPS, speaker alarm), `SosService`. Trigger in `patient`; alerts shown in `companion` (`FamilyLinkService`, SOS card) and `tv` (banner). A package, not a module |
| `captions` | New (Proposed) | Android | Offline speech-to-text for people talking through Suno |
| `app` | EarCast, restructured | Android app | Flavours `patient`, `companion`, `tv`; shared EarCast screens in `main` |

Package names move from `app.earcast.*` to `au.remotiq.suno.*`. The Gradle `namespace` and the `patient` application ID are `au.remotiq.suno`.

## 6. Audio pipeline

```
 Sources (any number)                  Patient's phone                                   Sinks (one active)

 Room mic ────────────────┐
 USB line-in ─────────────┤   decode     ┌───────┐   ┌────┐   ┌─────────────────────┐   ┌─────────┐   wired / USB earphones
 Person (companion Talk) ─┼─► + jitter ─►│ Mixer │──►│ NR │──►│ per-ear profile chain│──►│ Limiter │─► Bluetooth earbuds
 Tablet / phone sender ───┤   buffer     │ duck  │   └────┘   │ HPF → EQ → 3-band    │   └─────────┘   hearing aid (no-gain policy)
 TV app ──────────────────┤              └───────┘            │ comp → presence →    │                other Suno listeners
 Suno call peer ──────────┘                                   │ feedback → gain      │                (they re-apply their own profile)
                                                              └─────────────────────┘
 Normal phone call ──► Android telephony ──► hearing aid or phone call setting (outside Suno's audio path)
```

- **Mixer priority:** Person > TV / sender > Room. When a person talks, other sources duck. The patient can override.
- **Format on the link:** Opus, mono, 16–24 kHz, 20 ms frames, with sequence numbers and timestamps.
- **Noise reduction default:** RNNoise for Room (low delay). DPDFNet8 is offered where its extra delay matters less
  (TV, person and other remote sound) or by helper choice.

### 6.1 The audiogram rule

**Decided.** The applicable audiogram is the one that reaches the patient.

- The active profile on the **patient's own phone** is applied as the last processing step before the limiter, per ear,
  to **every** source: Room, TV, a person talking, a tablet sender, Suno-to-Suno calls and Media sound.
- **Senders send clean sound.** A sender's or companion's own profile is never applied to a patient's stream.
- With several listeners, each listener's phone applies that listener's own profile.
- The helper chooses the active profile behind the PIN. With no audiogram, a ready-made profile is the applicable one.
- All sources enter one chain, so no source can bypass the profile. A test asserts this for every `AudioSource` type.

Two exceptions, both shown in the UI:

| Case | What happens | Why |
|---|---|---|
| Normal cellular calls | A hearing aid, or the phone's own call sound setting, shapes the call | Android does not give any app access to call audio |
| Output is an ASHA / LE Audio hearing aid | Suno mixes, reduces noise and limits, but adds no profile gain | The aid applies its own fitting; both together would double the gain |

### 6.2 Latency (estimates, not measured)

| Path | Estimate |
|---|---|
| Room → wired earphones, noise reduction off | 30–60 ms |
| + RNNoise | about +30 ms |
| + DPDFNet8 | about +60–75 ms |
| Bluetooth earbuds (A2DP) output | about +100–250 ms |
| ASHA hearing aid output | about +160 ms (spec: 8 × the 20 ms connection interval) |
| Each mesh relay hop | about +40–80 ms |

Consequences: Suno recommends wired earphones for Room. Remote sources (person, TV) tolerate delay better. EarCast uses
Java `AudioRecord` / `AudioTrack`; moving to Oboe / AAudio is an option if measurements require it.

## 7. Outputs and Google ASHA

- ASHA streams phone → hearing aids only: G.722 at 16 kHz, mono or binaural, over Bluetooth LE. There is no microphone
  backlink. The hearing-aid vendor implements the peripheral; Android routes audio to it as an output device.
- Suno does not drive ASHA itself. It plays to the system output and detects the device type
  (`AudioDeviceInfo.TYPE_HEARING_AID`, `TYPE_BLE_HEADSET`).
- `OutputPolicy` selects **earphone mode** (full profile) or **hearing-aid mode** (no profile gain).
- Sharing to several listeners uses Suno-to-Suno streams. Auracast broadcast from an app is a system-only API *(To verify)*.

## 8. Link layer and mesh

### 8.1 Joining by QR (built in Phase 2)

1. The helper opens Helper mode → Settings → **Show join code**. The QR code is
   `suno://join?v=1&id=<session>&k=<secret>&n=<name>&w=<Wi-Fi Direct name>&p=<passphrase>&b=1`. The identity is
   created once and stored, so the code and remembered companions stay valid.
2. The companion scans it in Suno Companion → Family. It joins the patient's private **Wi-Fi Direct** group by name and
   passphrase (no discovery), and falls back to **Bluetooth LE** (finds the advert for the session id, opens L2CAP).
3. The companion sends Hello. The patient's phone shows **"*Name* wants to talk to you · Allow / Don't allow"**.
4. After Allow, the companion is remembered and its voice is played whenever the patient is listening.

Built: ask on the patient's phone. *Proposed:* typed 4-digit code, "ask the helper (PIN)", "contacts join without
asking".

### 8.1a Setting up a TV (built in Phase 3)

TVs have no camera, so the direction is reversed for the first step:

1. Suno TV shows its own **set-up QR** (`suno://join?...&t=1`) and hosts the link with it (Wi-Fi Direct and Bluetooth).
2. A family member, already joined to the patient, opens Suno Companion → Family → **Set up a TV** and scans it. The
   companion leaves the patient for a moment, connects to the TV, sends the patient's join code in an encrypted
   **Provision** message, waits for **Provisioned** (10 s limit), then reconnects to the patient.
3. The TV stores the patient's code and joins the patient's phone as device kind **TV**. The patient's phone shows
   **"*TV name* wants to share TV sound · Allow / Don't allow"**.
4. On the TV, **Start sharing TV sound** asks Android for capture consent (MediaProjection) and sends playback audio as
   a **media** stream. If the TV has no consent screen, Suno TV says so and points to the cable route.

A set-up code is never accepted as a patient code, and a TV only takes a patient code, not another set-up code.

### 8.2 Topology

- Audio uses short direct links, centred on the patient's phone (star).
- **Mesh mode (Decided):** devices forward for each other. Audio is relayed **at most 2 hops** to keep delay low.
  SOS, presence and control messages flood the **whole** mesh and cannot be switched off.
- **Built in Phase 4:** one relay level. A companion connected directly to the patient advertises the patient's session
  on Bluetooth LE with a relay flag. A phone that cannot reach the patient connects to it, and each of its connections
  is piped to a new connection from the relay to the patient (`LinkRelay`). The relay forwards encrypted bytes it
  cannot read; keys stay end to end and the patient still allows or refuses the far phone. Joiners prefer the patient's
  own advert; a phone that joined through a relay never relays, so everything stays within 2 hops, and the SOS reaches
  every device in the mesh (all are connections of the patient's phone).
- Google Nearby Connections does not relay by itself; forwarding is Suno code in `link`.
- "Anywhere" means within radio range of the mesh (a house or building). There is no online mode.

### 8.3 Roles on the link

| Role | Sends | Receives |
|---|---|---|
| Patient hub | Status, SOS, approvals, relayed audio | All audio sources, remote-help commands |
| Companion | Talk audio, shared playback or mic audio, remote-help commands, SOS replies | Patient status, SOS alerts |
| TV | TV playback audio (media stream) | The patient's join code from a companion (set-up), SOS banner |
| Relay (a companion) | Piped bytes of far phones, unread | Piped bytes for far phones |

Remote help from a companion is limited to listening on/off, volume up to the helper's cap, and place. The patient's
phone shows who changed what.

### 8.4 Transports

| Transport | Used for | Notes |
|---|---|---|
| Wi-Fi Direct (`link-android`) | **Default, preferred (built)** | Patient phone hosts a private group with fixed name and passphrase; TCP control + UDP audio on port 47620, bound to the group address only; 16-bit PCM |
| Bluetooth LE L2CAP (`link-android`) | **Default, fallback (built)** | Insecure L2CAP channel (no pairing) found through an advert with the session id; Opus via MediaCodec for the lower bandwidth |
| Google Nearby Connections (`link-nearby`) | Optional extra, in a separate `gms` build only **(Decided, not built)** | Needs Google Play Services |

Every frame on every transport is encrypted with AES-256-GCM. Each connection has its own key, derived with
HKDF-SHA256 from the join secret and a random salt from the companion, with replay protection. Audio from a phone that
is not approved, or that fails decryption, is dropped. The link needs **Android 10+** on both phones (Wi-Fi Direct
join by name, L2CAP, Opus encoder); on older phones the link options are hidden.

## 9. TV platforms

| TV | Suno TV app | How TV sound reaches Suno | State |
|---|---|---|---|
| Android TV / Google TV | Yes (`tv` flavour, built) | `AudioPlaybackCapture` (Android 10+) after the MediaProjection consent. Apps that opt out of capture, and most protected streaming apps, stay silent | To verify |
| Amazon Fire TV | Yes, via Amazon Appstore (same `tv` APK) | Same capture on Fire OS 8 (Android 11), if Fire OS shows the consent screen. The link needs no Play Services | To verify |
| Samsung (Tizen), LG (webOS) | No. Android apps do not run | Cable from the TV's headphone or optical output → USB-C audio adapter → a phone running Suno Companion → Share sound → **TV by cable** (built) | To verify |
| Any TV, including broadcast and set-top boxes | Not needed | The same cable route | To verify |

A phone cannot receive a TV's ordinary Bluetooth audio, because phones do not expose the A2DP sink role *(To verify)*.
Recommended order: cable sender first (works everywhere), then Android / Google TV, then Fire TV.

On the patient's phone, TV sound is a **media** stream: it is mixed with priority Person > TV > Room (TV ducks the room,
a talking person ducks the TV, about −12 dB with short ramps), before the patient's audiogram and the limiter. The Now
hearing card shows a TV chip when a TV is connected. TV speakers are not changed, so others watch at normal volume.

## 10. Phone app, contacts and SOS (`patient` flavour)

### 10.1 Phone app and contacts — Decided, built in Phase 5

The helper makes Suno the default phone app (`RoleManager.ROLE_DIALER`) in Helper mode → Settings → People and calls.
Code: `phone` package in `app` (`PhoneCalls`, `SunoInCallService`, `SunoCallScreeningService`, `CallActivity`,
`CallRules`, `DialRequests`); declared only in the patient manifest.

- **People:** large tiles with name and relationship; one tap calls. **Type a number** opens a large keypad. The Home
  dock has **Call <first person>** (or the SOS helper) and **People**. Other apps' `tel:` links and the dial action open
  Suno's keypad.
- **Incoming call:** full screen over the lock screen. Answer and Decline are tap buttons, with no swiping. Unknown numbers
  show "Never share bank details, PINs or OTP codes". Android plays the ringtone.
- **In a call:** hear it on Phone, Speaker, Earphones or Bluetooth (hearing aids connect as Bluetooth); call volume;
  mute; a large End call. One call at a time: a second incoming call is declined.
- **Helper settings:** People (from contacts or typed, reorder, remove); calls from People and contacts only (others
  are declined before ringing, but every call rings during an SOS); auto-answer People and contacts after about three
  rings (on earphones or Bluetooth if connected, else the speaker); and the SOS emergency call (section 10.2).
- The role gives control of call routing and volume and lets SOS place calls, including to the emergency number,
  directly. It does **not** give access to call audio ([section 6.1](#61-the-audiogram-rule)).
- Without the role, calls go through the phone's own app; People and the keypad still work (`ACTION_CALL` /
  `ACTION_DIAL`).
- Not built: call history screen, voicemail, a keypad during calls (menus), and Suno as the launcher (Proposed).

### 10.2 SOS — Decided, built in Phase 4

1. One tap on the vermillion octagon **SOS** opens a calm question: "Do you need help?" with **Yes, get help** and
   **No, go back**. No countdown.
2. On Yes, Suno (`SosController`):
   - alerts every device on the family link at once. The link starts hosting if it was not, and phones that reconnect
     during the SOS are told when they join. The place is sent again once GPS finds it;
   - stops listening (no amplified sound during SOS);
   - calls the helper (`ACTION_CALL` with CALL_PHONE, or the phone app prefilled without it);
   - finds the GPS place without internet (about 20 s, else the last known place);
   - texts the helper's numbers with the place. This is automatic when SEND_SMS is allowed; otherwise one tap opens
     the messaging app prefilled;
   - shows **Call 112** (the helper can change the number) as a big button. It opens the phone app prefilled, because
     Android does not let apps call emergency numbers directly.
3. **Alarm:** a big **Sound alarm / Stop alarm** button. The helper can make it start on its own. It plays only on the
   built-in speaker (`USAGE_ALARM`, preferred device); if the speaker cannot be chosen it stays silent and never plays
   into earphones or hearing aids.
4. Companions get a full-screen alert (`FamilyLinkService`, alarm-category notification) and an SOS card with the
   place, **Open map** and **I'm on my way**. Answers appear on the patient's SOS screen and on every other companion.
   The TV shows a banner.
5. **I'm safe now** ends it: everyone is told and the alarm stops. `SosService` keeps Suno running during the call.

Built in Phase 5: when Suno is the phone app it watches the helper call. If the call is not answered within 45 s or
ends unanswered, Suno calls the emergency number itself; with no helper number it calls straight away. Both need the
helper setting "call the emergency number if the helper does not answer" (on by default). A voicemail that picks up
counts as answered *(To verify)*. Without the role the emergency number stays a button. SEND_SMS is restricted on Google Play; a Play build would drop it and use the prefilled messaging app
*(To verify)*. GPS works without internet. The companion stays linked in the background with a notification, and
reconnects after 15 s, 30 s, then every 60 s. It does not start after a reboot until Suno Companion is opened.

## 11. User interface

### 11.1 Three layouts

| Layout | Flavour | Style |
|---|---|---|
| **Patient** | `patient` | Suno simple layout. New code. |
| **Helper mode** (⚙ + PIN) | `patient` | EarCast tabs: Listen / Profiles / Settings, with Suno sections added to Settings. Lock returns to the patient layout. |
| **Companion** | `companion` | EarCast tabs with **Family** first: Family / Listen / Profiles / Settings. |

Suno sections added to Helper → Settings: Patient screen, Contacts & calls, SOS, Mesh & devices, Lock, About.

Family tab: the patient's live status, Talk, Help (listening, volume, place), Share my sound, SOS alerts, Call.
Companion settings include quiet alerts: battery low, earphones off for 2 hours, Suno not used today *(Proposed)*.

### 11.2 Patient Home

| Element | Behaviour | State |
|---|---|---|
| Listen circle | Filled with halo rings and moving bars when on; plain circle with crossed ear when off | Decided (bars Proposed) |
| Volume | − and + buttons with six dots; no slider | Decided |
| Now hearing card | Shows the current source; only connected sources appear | Proposed |
| Place | Home, Park, Traffic, Night presets | Decided |
| Auto place | Suno chooses the place; manual choice in a sheet | Proposed; needs a sound classifier |
| Auto source | A person talking comes to the front | Proposed |
| Spoken words as text | Large captions while a person talks through Suno | Proposed; Hindi accuracy, size and battery to verify |
| Zero-tap | Listening starts and stops with the earphones | Proposed |
| Call helper, People | Bottom dock | Decided |
| SOS, Helper | Top bar | Decided |
| Home levels | 1 Simplest, 2 Standard, 3 Full; set by the helper | Proposed |
| Suno as the phone's home screen | Launcher role, optional | Proposed |
| Pictures mode | Symbols with one word; default on | Decided |

### 11.3 Accessibility rules for the patient layout — Decided

- One job per screen. No drawer, tabs, swipe-only gestures, long-press or timeouts.
- Main button at least 200 dp; other targets at least 72 dp. Text 24 sp body and up; works at 200% font size.
- Never colour alone: every state has an icon, a word, a shape and a colour.
- Colour-blind-safe palette (Okabe–Ito): blue `#0072B2`, orange `#E69F00`, bluish green `#009E73`, vermillion `#D55E00`.
  No red/green pairs. Text contrast at least 7:1.
- Themes: Standard, Dark, Yellow on black. SOS keeps the same vermillion and octagon icon in every app.
- Calm: no flashing; motion is slow and stops when the system setting asks for reduced motion.
- Read-aloud (offline TTS), full TalkBack labels, Hindi and English.

## 12. Safety and privacy rules

Treat a violation of any of these as a critical bug.

- Nothing reaches an output without passing the limiter. All limits live in `AudioLimits`.
- Amplified sound never plays through the phone speaker. Listening pauses when earphones disconnect.
- Gain starts from silence and ramps. Volume cannot exceed the helper's cap, locally or by remote help.
- The patient's microphone audio never leaves the patient's phone, except in SOS and calls.
- No recording, no upload, no analytics, no account. The patient and companion apps declare `INTERNET` only because
  Android requires it for any socket, including the local Wi-Fi Direct group; Suno makes no internet connections and
  binds only to the group address. The TV app declares no network permission yet.
- Joining requires approval. The patient's phone shows who is connected and what a companion changed.
- Helper mode locks again when it is left.
- Hearing-aid output never receives Suno's profile gain.

## 13. Licensing

**Decided:** Suno is free and open-source software under **GPL-3.0-only**. This matches EarCast, which states
"GPL-3.0" without "or later".

- The repository root has the verbatim GPLv3 text in `LICENSE`.
- Source files carry `// SPDX-License-Identifier: GPL-3.0-only`. Files taken from EarCast keep their origin; new
  files are "Copyright (C) 2026 Suno contributors" unless the owner chooses another holder name.
- Credit in the README and the About screen: "Based on EarCast by Octavian Negru, which was inspired by OpenHearing."
- Keep third-party notices and model checksums from EarCast:

| Component | Licence |
|---|---|
| RNNoise, SpeexDSP | BSD |
| sherpa-onnx | Apache-2.0 |
| ONNX Runtime | MIT |
| DPDFNet model files | Bundled licence text is Apache-2.0; upstream model-card terms not checked *(To verify)* |
| eSpeak-NG (inside the sherpa runtime) | GPLv3 |

- **Contributions** *(Proposed)*: contributions are accepted under the same licence, with a `Signed-off-by` line
  (Developer Certificate of Origin).

### 13.1 Distribution

| Channel | Fit | What it needs |
|---|---|---|
| Google Play | GPL apps are allowed | Policy review for the phone-app role, SMS and background microphone |
| F-Droid | Natural home for a GPL app | No proprietary libraries, so a build without Google Play Services (`link-direct`). Native code built from source: EarCast ships a **prebuilt** sherpa-onnx / ONNX Runtime archive, which F-Droid normally does not accept *(To verify)* |
| GitHub releases | Simple | Signed APKs per flavour |

### 13.2 Open risk: Google Play Services and the GPL

Google Nearby Connections is part of Google Play Services, which is proprietary. Shipping it inside a GPLv3 app is
widely considered a licence conflict unless the copyright holders allow it. This is not legal advice; get a legal
opinion before the first release.

Options:

1. **Additional permission (GPLv3 section 7).** Suno's own code adds a permission to link with Google Play Services.
   EarCast-derived code needs the same permission from its copyright holders: Octavian Negru, and the OpenHearing
   author if EarCast contains copied OpenHearing code. Ask early.
2. **No Google transport by default.** Use `link-direct` (Wi-Fi Direct / LAN / BLE) in all builds, with Nearby only in
   an optional `gms` build. This also suits F-Droid and Fire TV.

Either way, `link` hides the transport behind one interface, so the choice can wait until Phase 2 starts.

## 14. Platform facts to verify on real devices

1. End-to-end latency for each path in [section 6.2](#62-latency-estimates-not-measured).
2. The feedback-suppressor fix, with real earphones and gain.
3. Nearby Connections: stream delay, number of simultaneous listeners, behaviour while A2DP earbuds are in use.
4. `AudioPlaybackCapture` on target TVs and tablets, and which apps block capture.
5. Fire OS version and capture support on current Fire TV devices.
6. Phones not exposing A2DP sink; Auracast source being system-only.
7. Default phone-app role behaviour across OEMs (Samsung, Xiaomi, Pixel); `CallScreeningService` bound for the
   default phone app; auto-answer; full-screen incoming calls on the lock screen; dual SIM; voicemail counted as
   answered in the SOS escalation.
8. `SEND_SMS` policy route on Google Play for SOS; full-screen alerts on Android 14+ (`USE_FULL_SCREEN_INTENT` is
   limited to calling and alarm apps); alarm routing to the speaker only; background link battery; relay range.
9. Hearing-aid detection and hearing-aid mode with ASHA and LE Audio aids.
10. Offline Hindi speech recognition quality and cost (captions).
11. CPU, heat and battery of each noise-reduction engine on budget phones.
12. Calibration: all levels are digital, not dB SPL.
13. Family link: Wi-Fi Direct group creation and join by name on real phones, with and without home Wi-Fi; Bluetooth
    LE advert and L2CAP on different brands; Opus encoder frame size and the decoder's output rate; end-to-end voice
    delay; several companions at once; reconnect after screen-off; battery and heat while hosting.

## 15. Build order

Each slice is usable on its own.

| Slice | Contents |
|---|---|
| 0 | Fork EarCast with history, rename to `au.remotiq.suno`, add flavours, fix the safety issues in [section 4.2](#42-changed-or-fixed), build and tests green |
| 1 | Patient layout: Listen, volume, places, Home levels, zero-tap, Pictures mode, Hindi, themes, Helper PIN |
| 2 | `audio-graph` + `link`: companion joins by QR and talks; captions if verified |
| 3 | TV sound: cable sender in `companion`, then the `tv` flavour **(built, not on a device)** |
| 4 | SOS and companion alerts; mesh relay **(built, not on a device)** |
| 5 | Phone app, contacts, home-screen role (store policy review needed) **(phone app and People built, not on a device; launcher not built)** |

Checks for every slice: `./gradlew ktlintCheck detekt test testDebugUnitTest` plus assembling all three flavours.
Anything involving real sound, radios or calls needs physical devices and a written device-test checklist.

## 16. Decision log

| Date | Decision |
|---|---|
| 2026-11-21 | Native Android (Kotlin). Phone hearing-aid app first. |
| 2026-11-21 | EarCast is the baseline; OpenHearing is a reference only. |
| 2026-11-21 | Align with Google ASHA; hearing aids are an output with no stacked gain. |
| 2026-11-21 | Offline device-to-device streaming with Google Nearby Connections; join by QR with approval. |
| 2026-11-21 | TV companion app; cable route for TVs that cannot run it. |
| 2026-11-21 | Place modes: Home, Park, Traffic, Night. |
| 2026-11-21 | SOS on the patient Home. |
| 2026-11-21 | Mesh mode: audio up to 2 hops, SOS across the whole mesh. |
| 2026-11-21 | Suno is the default phone app and contacts for the patient. |
| 2026-11-21 | Keep DPDFNet8 + ONNX runtime, Media sound EQ and EarCast's UI. |
| 2026-11-21 | Suno layout is for the patient only; Helper mode and Companion use the EarCast layout. |
| 2026-11-21 | The patient's applicable audiogram is applied to everything the patient hears. |
| 2026-11-21 | Companion apps are built as product flavours (`patient`, `companion`, `tv`); this replaces the setup-time mode question. |
| 2026-11-21 | Primary package name is `au.remotiq.suno`; flavours use the suffixes `.companion` and `.tv`. |
| 2026-11-21 | Suno is free and open source under GPL-3.0-only (matching EarCast). |
| 2026-11-21 | The device-to-device link works without Google services by default (Wi-Fi Direct / Bluetooth); Nearby is an optional `gms` build. |
| 2026-11-21 | Phase 0 done: EarCast merged with history, renamed, three flavours, safety fixes. |
| 2026-11-21 | Phase 1 done: patient Home (Listen, volume, places, home levels, pictures/words, three themes, Hindi), helper PIN, Helper mode with Lock, Patient screen settings, screenshot tests. Auto place and the Now-hearing card wait for Phase 2 sources. |
| 2026-11-21 | Phase 2 builds both offline transports, Wi-Fi Direct (preferred) and Bluetooth LE (fallback). |
| 2026-11-21 | Every link frame is encrypted (HKDF + AES-GCM per connection); the link needs Android 10+. |
| 2026-11-21 | Patient and companion apps declare INTERNET only for local sockets; no internet connections are made. |
| 2026-11-21 | Phase 2 done: join by QR with Allow / Don't allow, companion Talk, voices mixed before the patient's chain, Now-hearing card and talking banner, companion Family tab, screenshot tests. Not on a device yet. |
| 2026-11-21 | TV sound is a separate media stream with priority Person > TV > Room, mixed before the patient's chain. |
| 2026-11-21 | Companion "Share sound": TV by cable (USB-C audio adapter), microphone near the TV, or this phone's playback (MediaProjection). |
| 2026-11-21 | TVs are set up by a companion phone scanning the TV's set-up QR and handing over the patient's code over the encrypted link (TVs have no camera). |
| 2026-11-21 | Phase 3 done: media streams, companion Share sound and Set up a TV, Suno TV app (10-foot screen, remote focus, playback capture), screenshot tests. Not on a device yet. |
| 2026-11-21 | SOS texts are sent automatically when SEND_SMS is allowed, otherwise the messaging app opens prefilled; the patient app declares SEND_SMS (GitHub / F-Droid builds). |
| 2026-11-21 | The SOS alarm is a button (the helper can make it automatic), so it does not drown out the call; speaker only, silent if the speaker cannot be chosen. This changes "sounds a loud alarm" in 10.2. |
| 2026-11-21 | Emergency number is a button that opens the phone app; automatic escalation waits for the phone-app role (Phase 5). |
| 2026-11-21 | The companion stays linked in the background (foreground notification, reconnect backoff) so SOS alerts arrive. |
| 2026-11-21 | Mesh relay: one level over Bluetooth LE, transparent and end-to-end encrypted, at most 2 hops. |
| 2026-11-21 | Phase 4 done: SOS (patient), SOS alerts and I'm on my way (companion), TV banner, background link, relay, screenshot tests. Not on a device yet. |
| 2026-11-21 | People is Suno's own list chosen by the helper (from contacts or typed); phone contacts are used only for caller names and "contacts only". |
| 2026-11-21 | With "contacts only", unknown callers are declined before ringing, except during an SOS. One call at a time. |
| 2026-11-21 | SOS escalation: with Suno as the phone app, an unanswered helper call leads to an automatic emergency call (helper setting, on by default). |
| 2026-11-21 | Phase 5 done: Suno as the phone app (incoming and in-call screens, routes, volume, mute, screening, auto-answer), People and keypad, Home call dock, helper People and calls settings, SOS escalation, screenshot tests. Launcher role not built. Not on a device yet. |
