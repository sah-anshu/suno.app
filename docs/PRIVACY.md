# Privacy Policy

_Last updated: 2026-11-21 (Suno 0.1.0)_

Suno is three apps built from one code base: **Suno** (for the person being helped, called the patient here),
**Suno Companion** (for family and carers) and **Suno TV**. Suno works without internet. It has no accounts, no
analytics, no advertising and no purchases, and it has no server: nothing is sent to the publisher.

## What stays on the patient's phone

- **Microphone.** While listening is on, Suno captures sound from the chosen microphone, processes it in memory and
  plays it to the connected earphones or hearing aid. It is not recorded or saved, and it never leaves the patient's
  phone. The only exceptions are a phone call or an SOS call the patient makes or answers, which Android handles.
- **Hearing profiles, settings, helper PIN.** Stored in the app's private storage only. The PIN is stored as a salted
  hash. App data is excluded from Android cloud backup and device transfer.
- **People and SOS numbers.** Names and numbers the helper enters for People and SOS stay in the app's private storage.
- **Contacts.** If allowed, Suno looks up the names of people who call, on the phone itself. It does not copy the
  contact list.

## Between the family's own devices (family link)

- A family member's phone or a TV joins the patient's phone only after scanning its QR code **and** the patient's
  phone allowing it. Devices talk directly over Wi-Fi Direct or Bluetooth, never over the internet.
- What travels: a family member's voice and shared sound (for example TV sound) **to** the patient's phone, SOS alerts
  and "I'm on my way" answers, and the device names people chose. Every message is encrypted end to end
  (AES-256-GCM). A family phone that relays for another phone cannot read what it forwards.
- The patient's microphone sound is **never** sent over the family link.
- Suno asks for location permission on older Android versions only because Android requires it for Wi-Fi Direct and
  Bluetooth scanning. Suno does not use location for the family link.

## SOS

When the patient taps SOS and confirms:

- the patient's phone sends an alert to the family devices that are linked, with the phone's GPS position if it was
  found;
- if the helper allowed it, a text message with that position goes to the numbers the helper chose, through the
  phone's own SMS;
- the phone calls the helper, and the emergency number if the helper set that up.

These are ordinary calls and texts made by the phone. Your mobile operator handles them under its own terms. Suno
keeps no history of SOS events.

## Phone calls

If the helper makes Suno the phone app, Android gives Suno control of the call screens, call routing and call volume.
Suno never gets the call sound. With "calls from contacts only" on, Suno declines unknown callers before the phone
rings. Those calls still appear in the phone's call log.

## Suno TV

Suno TV sends what the TV plays to the patient's phone only after the TV owner allows screen and sound capture in
Android's own prompt. It stores the patient's join code so it can reconnect.

## Permissions and why

| Permission | App | Why |
|---|---|---|
| Microphone (`RECORD_AUDIO`) | all | Listening (patient); Talk and Share sound (companion); capture (TV) |
| Nearby devices / Wi-Fi / Bluetooth; location on Android 12 and older | all | The family link (Wi-Fi Direct, Bluetooth LE). No location use |
| `INTERNET` | all | Android requires it for any network socket, including the local Wi-Fi Direct group. No internet connections are made |
| Camera | companion | Scan QR codes |
| Location (`ACCESS_FINE_LOCATION`) | patient | GPS position in an SOS |
| Phone calls (`CALL_PHONE`), phone-app role | patient | Calling People, the helper and the emergency number; call screens |
| Contacts (`READ_CONTACTS`) | patient | Names of callers |
| SMS (`SEND_SMS`) | patient | SOS text with the position (not in a Google Play build) |
| Notifications, foreground services | all | Visible notifications while listening, talking, sharing, linked, or in an SOS |
| Full-screen notifications | companion | Show an SOS alert over the lock screen |

## Retention and deletion

Everything stays until you delete it in the app, clear Suno's storage in Android Settings, or uninstall. Leave in the
companion's Family tab forgets the patient's code. Remove in Helper mode, Settings forgets a family device.

## Children

Suno can be set up by a parent or carer for a child. It collects no personal data and sends nothing to the publisher.

## Contact

Before publication the publisher must add its public name and a monitored privacy contact here, publish this
policy at a public HTTPS address, and set `sunoPrivacyPolicyUrl` and `sunoSupportEmail` for the release build.

> Suno is a hearing-assistance tool, not a medical device. See the README and docs/SAFETY.md.
