# Manent: the app

The phone app of Manent. It links a wearable that streams speech (the Manent ring,
band, card and pendant; an Omi pendant for now, while there is no prototype), records
conversations, listens for wake words and sends each command to Home Assistant, a
webhook or the phone itself. Recordings stay on the phone; an Omnesis gateway can be
connected to send them there.

## Layout

| Path | What |
| --- | --- |
| `app/` | The app: `wearable/` (the device seam and its adapters), `omnesis/` (the optional Omnesis connection), `ui/` |
| `core-transport/` | Client of the Omnesis gateway: HTTP, device socket, wire types |
| `core-pairing/` | Pairing the phone with a gateway |
| `core-designsystem/` | Theme and shared components |
| `feature-recording/` | Recording from the phone or the wearable, the outbox and its upload |
| `feature-omi/` | The Omi pendant: link and audio stream |
| `feature-assist/` | Wake words and voice commands |

## Build

Needs JDK 17 and the Android SDK (`sdk.dir` in `local.properties`).

```
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
```

## Omnesis

Optional. On the gateway, recordings need `OMNESIS_EXPERIMENTAL=1` and transcription on.
Pair the phone as a device of its own:

```
omnesis devices pair --kind android --scopes read,write:omnesis-recordings,write:omnesis-notes
```

then paste the QR payload, or the URL and code, under Omnesis → Connect.

## Licence

MIT, see `LICENSE`.
