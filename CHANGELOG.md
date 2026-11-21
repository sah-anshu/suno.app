# Changelog

## 0.1.0 (versionCode 3), first Suno test release

Not yet checked on real devices. For testers only. Suno is not a medical device.

- **Three apps** from EarCast's code base: Suno (patient), Suno Companion and Suno TV.
- **Patient Home**: one Listen button, volume steps, places (Home, Park, Traffic, Night), three home levels, pictures
  or words, three colour themes (including yellow on black), Hindi, a helper PIN that opens EarCast's full screens.
- **Family link without Google services**: a family phone scans the patient's QR code, the patient's phone allows it,
  and the voice plays in the patient's earphones with the patient's own hearing profile. Wi-Fi Direct with Bluetooth
  LE fallback, end-to-end encryption, and a relay through another family phone (at most two hops).
- **TV sound**: from a phone by cable, from a microphone near the TV, or from the Suno TV app, which a family phone sets
  up. Person > TV > room priority.
- **SOS**: a calm question first, then alerts to family phones and the TV, the GPS position by text, a call to the
  helper and, as the phone app, the emergency number if the helper does not answer. A speaker-only alarm button.
  Family phones show a full-screen alert with "I'm on my way".
- **Phone app**: People and a large keypad, incoming and in-call screens with tap buttons, call routing to earphones,
  speaker or hearing aid, calls from contacts only, auto-answer.
- Safety fixes from the study: the limiter is always last, no amplified sound on the phone speaker, and a fixed feedback
  guard.
