# Manent: the app

The phone app of [Manent](https://manentlabs.it). It links a wearable that streams speech
(the Manent ring, band, card and pendant; an [Omi](https://www.omi.me) pendant for now,
while there is no prototype) and does two things with what it hears:

- **Voice commands.** Say a wake word, then a command. Each wake word goes to a destination
  of its own: Home Assistant's Assist, a webhook, a [Hermes Agent](https://github.com/NousResearch/hermes-agent)
  (its answer is read aloud), or the phone itself (timers, alarms, navigation, music, calls,
  calendar, notes). Only the command after a wake word leaves the
  phone; nothing is recorded.
- **Conversations.** Record from the pendant or the phone's microphone, and send the
  recording to an [Omnesis](https://github.com/omnesis-dev/Omnesis) gateway, which
  transcribes it. With "capture all day" on, the pendant records each conversation it
  hears, and a tap marks a moment.

## Layout

Everything is in one module, `app/`, package `it.manentlabs.app`:

| Package | What |
| --- | --- |
| `wearable/` | The device seam (`Wearable`) and the Omi adapter: Bluetooth link, audio frames, haptics, the service that keeps it linked |
| `audio/` | Opus decoding, resampling to 16 kHz, voice activity (Silero VAD), AAC files, the fan-out that lets several parts of the app hear one wearable |
| `recording/` | The recorder, its foreground service, the upload queue and the worker that sends it to the gateway |
| `gateway/` | A small client of the Omnesis gateway: pairing, certificate pinning, the calls the app makes |
| `voice/` | Wake words (openWakeWord models on ONNX Runtime), command capture, Home Assistant, Hermes Agent, webhooks, phone actions |
| `speech/` | Replies read aloud |
| `security/` | Secrets sealed with a key in the Android Keystore |
| `ui/` | Screens, navigation and the design system in `ui/theme` |

## Build

Needs JDK 17 and the Android SDK (`sdk.dir` in `local.properties`).

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

A release build is signed when `keystore.properties` (never committed) names the upload key:
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## Omnesis

Optional, needed only to record conversations and for voice commands that go to a webhook,
Hermes Agent or the phone (the gateway transcribes them). On the gateway, recordings need
`OMNESIS_EXPERIMENTAL=1` and transcription on. Pair the phone as a device of its own:

```
omnesis devices pair --kind android --scopes read,write:omnesis-recordings,write:omnesis-notes
```

then paste the pairing payload, or the URL and code, under Omnesis → Connect.

## Hermes Agent

A wake word can go to Hermes Agent's OpenAI-compatible API server. In `~/.hermes/.env`:

```
API_SERVER_ENABLED=true
API_SERVER_KEY=<a long random key>
API_SERVER_HOST=0.0.0.0   # the default, 127.0.0.1, is out of the phone's reach
```

then run `hermes gateway` and put `http://<host>:8642` and the key under Voice commands →
Hermes Agent. The command goes to `/v1/chat/completions` as text, with a fixed
`X-Hermes-Session-Key` so the agent's memory sees one Manent channel, and the reply is read
aloud. The key opens every tool the agent has: keep the server on your own network or behind
HTTPS.

## Wake-word models

The models are not part of the app: it downloads them on request from where they are
published, checks their SHA-256, and shows each one's licence first. "ok home" comes from
the Home Assistant community collection (MIT); "hey jarvis", "alexa", "hey mycroft" and
"hey rhasspy" are openWakeWord's pre-trained models, licensed for non-commercial use only
(CC BY-NC-SA 4.0). Any openWakeWord model can also be imported.

## Licence

MIT, see `LICENSE`. Third-party files bundled in the app, with their licences in
`app/src/main/assets/licences/`:

- Silero VAD model (MIT)
- Bodoni Moda, Titillium Web and IBM Plex Mono fonts (SIL Open Font License 1.1)
