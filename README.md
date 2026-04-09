# PezhvakP2P

Secure mesh communications for Android. Works with or without internet.

## Transports

| Transport | Range | Speed | Requires |
|-----------|-------|-------|----------|
| BLE Mesh | ~100m/hop, 7 hops | ~1 Mbps | Bluetooth LE |
| WiFi Direct | ~300m | ~250 Mbps | WiFi |
| Nostr Relays | Global | Internet | Internet |

All three run simultaneously. Messages are deduplicated — whichever transport delivers first wins.

## Features

- **DMs** — NIP-44 E2E encrypted, delivered via all available transports
- **Groups** — Symmetric group key, sealed per member
- **Channels** — Signed broadcast, mesh-distributed
- **Forums** — Threaded discussions (Nostr kind 1003/1004)
- **Voice/Video Calls** — WebRTC (DTLS-SRTP), signaling over Nostr
- **Screen Sharing** — MediaProjection API
- **News** — Cryptographically signed server content, auto-distributed via mesh; tampered items are dropped at every hop

## Security

- **Identity** — secp256k1 keypair (Nostr-compatible), stored in hardware-backed `EncryptedSharedPreferences`
- **Encryption** — NIP-44 v2: ECDH → HKDF → AES-256-GCM, length-padded
- **Integrity** — Schnorr signature on every message; relay nodes verify before forwarding
- **Database** — SQLCipher, key derived from user identity via HKDF
- **News anti-tamper** — `SHA-256(title+summary+content)` verified against server Schnorr signature
- **No cloud backup** of keys or database

## Architecture

```
app/src/main/kotlin/com/pezhvak/p2p/
├── core/
│   ├── crypto/         # secp256k1, NIP-44, AES-GCM, HKDF
│   ├── identity/       # KeyManager (EncryptedSharedPreferences)
│   └── db/             # Room + SQLCipher entities & DAOs
├── transport/
│   ├── nostr/          # WebSocket relay pool, event signing/verify
│   ├── ble/            # BLE Mesh (GATT server+client, flood routing)
│   └── wifidirect/     # WiFi P2P (TCP sockets, dynamic GO negotiation)
├── calls/              # WebRTC voice/video + screen share
├── news/               # Signed news distribution
└── ui/                 # Compose screens: Home, Chat, Channels, Forums, News, Settings
```

## Build

```bash
# Requirements: JDK 17+, Android SDK 34
export ANDROID_HOME=~/android-sdk

./gradlew assembleRelease
# Output: app/build/outputs/apk/release/
```

- minSdk 21 (Android 5.0+)
- Kotlin 2.0.21 · AGP 8.5.2 · Compose BOM 2024.09.03 · Hilt 2.52

## Install

Download the APK for your device from [Releases](https://github.com/TadB0x/pezhvakp2p/releases):

| APK | Use for |
|-----|---------|
| `arm64-v8a` | Most Android phones (2016+) |
| `armeabi-v7a` | Older / budget phones |
| `universal` | Any device |
| `x86_64` / `x86` | Emulators |

Enable **Install from unknown sources** in Android Settings before sideloading.

## Before Production

1. Replace `TRUSTED_NEWS_SERVER_KEY` in `NewsRepository.kt` with your server's real pubkey
2. Re-enable `isMinifyEnabled = true` in `app/build.gradle.kts`
3. Store `app/pezhvak-release.jks` securely — it is gitignored
