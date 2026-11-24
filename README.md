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
| `vendor/` | Modules copied from Omnesis in their original packages. Never edit by hand: see below |
| `patches/` | Changes applied to `vendor/` after each copy |
| `scripts/vendor.sh` | Copies `vendor/` again from an Omnesis checkout and applies `patches/` |
| `VENDORED.md` | Which ref and commit each vendored module came from |

## Build

Needs JDK 17 and the Android SDK (`sdk.dir` in `local.properties`).

```
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
```

## Refreshing the vendored code

```
scripts/vendor.sh                       # upstream/main and baldarn/omi-assist-commands
UPSTREAM_REF=v0.9.0 scripts/vendor.sh   # another upstream ref
git diff vendor/                        # what upstream changed
```

To change a vendored file, edit it, then save the change as a patch:
`git diff --relative=vendor vendor/ > patches/NNNN-what.patch`, starting from a tree where
`vendor/` matches the last copy.

## Omnesis

Optional. On the gateway, recordings need `OMNESIS_EXPERIMENTAL=1` and transcription on.
Pair the phone as a device of its own:

```
omnesis devices pair --kind android --scopes read,write:omnesis-recordings,write:omnesis-notes
```

then paste the QR payload, or the URL and code, under Omnesis → Connect.

## Licence

MIT, see `LICENSE`. The vendored code comes from Omnesis, which is AGPL-3.0 upstream; the copy here is under MIT.
