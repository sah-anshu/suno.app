# Google Play release checklist

Local build checks do not replace Play Console review or device tests. See [RELEASE.md](RELEASE.md). Play is not the
first channel for Suno 0.x; this list records what Play would need.

## Publisher inputs

- Upload keystore and `keystore.properties`; use Play App Signing and keep secure backups.
- Public publisher identity and monitored contact email. Finish [PRIVACY.md](PRIVACY.md), publish it at a public HTTPS
  page, and set `sunoPrivacyPolicyUrl` and `sunoSupportEmail` for the build.
- Three listings: `au.remotiq.suno`, `au.remotiq.suno.companion`, `au.remotiq.suno.tv` (TV: Android TV form factor,
  banner, TV screenshots). Application IDs are permanent.

## Permissions that need declarations or changes

| Permission or feature | App | Play position | Action |
|---|---|---|---|
| `SEND_SMS` | patient | Restricted to the default SMS app or approved exceptions | Remove it in a Play build (SOS opens the messaging app prefilled), or apply for an exception |
| Phone-app role, `CALL_PHONE`, `READ_CONTACTS`, call screening | patient | Allowed for a real phone app; explain in the listing and permissions form | Describe People, call screens, SOS calls |
| `FOREGROUND_SERVICE_MICROPHONE` | all | Declaration with video | Listening (patient), Talk / Share (companion) |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | companion, TV | Declaration | Share this phone's or TV's sound, after Android's prompt |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | companion, patient | Declaration | Staying linked for SOS alerts; SOS service |
| `FOREGROUND_SERVICE_LOCATION`, fine location | patient | Declaration; prominent disclosure | GPS position only during an SOS |
| `USE_FULL_SCREEN_INTENT` | companion | Limited to calling and alarm apps from Android 14 | Declare SOS alerts as alarms; otherwise they show as notifications |
| Location on Android 12 and older | all | Must explain | Required by Android for Wi-Fi Direct and Bluetooth scanning; not used |
| `INTERNET` | all | Data safety must be accurate | Local sockets only; no data collection |

## Console declarations

- Health apps declaration and an accurate description: amplification, uncalibrated hearing check, not a medical device.
- Data safety: no data collected or shared with the publisher. Audio and messages between the family's own devices
  are end-to-end encrypted and do not reach a server. SOS texts and calls go through the phone's operator.
- Target audience: adults and carers; children only with a parent or carer.
- Free, no ads, no billing.

## Validation before rollout

- Build the signed AAB per app (`-PsunoSplitApks=false`), validate with bundletool, check 16 KB page alignment
  (`scripts/check_native_alignment.py`, `zipalign -c -P 16`).
- Release lint, unit tests, ktlint, detekt; Play pre-launch report.
- Run [DEVICE_TESTING.md](DEVICE_TESTING.md) on real devices, including Android 16 and API 26.
- Closed testing (12 testers for 14 days) for new personal accounts.

## Official references

- [Target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [SMS and Call Log permissions](https://support.google.com/googleplay/android-developer/answer/10208820)
- [Foreground services declaration](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Full-screen intent](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Health declaration](https://support.google.com/googleplay/android-developer/answer/14738291)
- [16 KB support](https://developer.android.com/guide/practices/page-sizes)
