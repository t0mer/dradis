# DRADIS

**DRADIS** turns an Android phone into a remotely controllable MQTT endpoint.
A foreground service holds a persistent MQTT connection, reacts to inbound
command topics (send SMS, get location, find‑my‑phone, take photo, push
notification, speak text), and publishes telemetry (battery, charging, Wi‑Fi,
sensors, location, online status).

It is a native replacement for the third‑party **Zanzito** app. The topic prefix
is configurable and defaults to `dradis`; set it to `zanzito` to stay
**wire‑compatible** with the legacy backend (Mosquitto + a Flask `smssender.py`
microservice publishing to `zanzito/<device>/sendsms/...`).

A defining feature: DRADIS **picks one of two brokers based on the Wi‑Fi network**
— a **LAN** broker when on a known home SSID, a **WAN** broker otherwise — and
reconnects automatically as the phone moves between networks.

> ⚠️ Personal, **sideloaded** app. `SEND_SMS`, background location and the camera
> are heavily restricted on Google Play, so this is **not** intended for Play
> distribution.

---

## Screenshots

| Status | Settings | Logs |
|---|---|---|
| ![Status](assets/screenshots/status.png) | ![Settings](assets/screenshots/settings.png) | ![Logs](assets/screenshots/logs.png) |

- **Status** — live connection state, selected broker (LAN/WAN), current SSID /
  host, and one‑tap access to every permission DRADIS needs.
- **Settings** — grouped into **Basic · Update modes · Outbound · Inbound ·
  Behaviour**; device name, topic prefix, home SSIDs, both brokers
  (host / port / auth / TLS + CA certificate), the update interval, and each
  feature with its own options. Credentials are masked.
- **Logs** — a live, in‑app mirror of every inbound/outbound MQTT message,
  independent of `logcat`.

---

## Features

| Feature | Description |
|---|---|
| **Dual MQTT brokers** | LAN broker on a known home SSID, WAN broker otherwise; auto‑switch + auto‑reconnect on network change. |
| **Send SMS** | JSON `{phone,text}`, or the legacy Zanzito topic‑path form; optional notification after sending. |
| **Location** | On‑demand fix and periodic publishing on the update interval; high‑accuracy toggle. |
| **Find my phone** | Loud, looping alarm + vibration that can bypass silent / Do‑Not‑Disturb; configurable duration and **ringtone**. |
| **Battery & charging** | Level (0–100), charging flag, charge type (AC / USB / Wireless / None). |
| **Wi‑Fi** | Connection state + SSID. |
| **Sensors** | Step counter, step detector and significant‑motion. |
| **Take photo** | Headless capture from the front or rear camera, resized/compressed, published as base64 JPEG; default camera setting. |
| **Push notification** | Show a notification on the device's shade from a remote message; optionally read it aloud via TTS. |
| **Text‑to‑speech** | Speak a remote message aloud. |
| **Telemetry** | Battery / Wi‑Fi / device‑info / sensors / location, each on its own topic — on connect, on change, on demand, and on the heartbeat interval. |
| **Autostart** | Reconnects after boot (when enabled). |

---

## How it works

- A single **foreground service** (`MqttService`) owns the HiveMQ MQTT 3.1.1
  client for the whole app lifetime and shows a persistent notification.
- **Broker selection (`BrokerSelector` + `NetworkMonitor`):** on every network
  change the current Wi‑Fi SSID is resolved; if it is in the configured **home
  SSID** list → **LAN** broker, otherwise → **WAN** broker. Switching networks
  tears down and reconnects to the right broker (debounced to ride out flaps).
- **Reconnect:** HiveMQ auto‑reconnect with a tightened backoff (1 s → 20 s cap)
  and a 45 s keep‑alive, so transient drops recover in seconds and the
  connection survives cellular NAT timeouts.
- **LWT:** on connect it publishes `status=1` (retained); the Last‑Will sets
  `status=0` (retained) so subscribers learn when the phone drops off.
- **Heartbeat:** when *periodic updates* are on, telemetry **and** location are
  published every *update interval* (default 90 s).
- Command handlers are isolated — a failure in one (e.g. camera) never drops the
  MQTT connection.

---

## MQTT topic contract

Topics are `<prefix>/<device>/<leaf>` — e.g. `dradis/phone/sendsms`. Both `prefix`
(default `dradis`) and `device` are configurable. QoS 1; `status` and `version`
are retained, everything else is not.

### Inbound — commands the app subscribes to

| Purpose | Topic | Payload |
|---|---|---|
| Send SMS (preferred) | `<prefix>/<device>/sendsms` | `{"phone":"+972…","text":"hello"}` |
| Send SMS (legacy) | `<prefix>/<device>/sendsms/<phone>` | raw string = message text |
| Get location now | `<prefix>/<device>/getlocation` | empty / `{}` |
| Find phone | `<prefix>/<device>/ping` | optional `{"seconds":30}`; `0` stops |
| Take photo | `<prefix>/<device>/takephoto` | `{"camera":"front"｜"rear"}` (empty → default) |
| Push notification | `<prefix>/<device>/notify` | `{"title":"…","text":"…","id"?:N}` or raw text |
| Text‑to‑speech | `<prefix>/<device>/say` | `{"text":"…"}` or raw text |
| Force telemetry | `<prefix>/<device>/getstatus` | empty |

### Outbound — what the app publishes

| Purpose | Topic | Payload | Retained |
|---|---|---|---|
| Online status | `<prefix>/<device>/status` | `1` online / `0` LWT offline | ✅ |
| App version | `<prefix>/<device>/version` | e.g. `2026.6.9` | ✅ |
| Device info | `<prefix>/<device>/device_info` | `{time, device_info, screen_locked}` | — |
| Battery | `<prefix>/<device>/battery` | `{battery_level, charging, charge_type}` | — |
| Wi‑Fi | `<prefix>/<device>/wifi` | `{connected, ssid}` | — |
| Sensors | `<prefix>/<device>/sensors` | `{step_counter, steps_detected, motion_detected, time}` | — |
| Location | `<prefix>/<device>/location` | `{lat, lon, accuracy, time}` | — |
| Photo | `<prefix>/<device>/photo` | `{camera, time, jpeg_b64}` | — |
| SMS result | `<prefix>/<device>/sendsms/result` | `{phone, ok, error?}` | — |
| Log (diagnostics) | `<prefix>/<device>/log` | free text | — |

### Payload examples

```jsonc
// battery
{ "battery_level": 76, "charging": true, "charge_type": "USB" }   // AC|USB|Wireless|None
// wifi          (ssid is null off Wi-Fi or when unreadable)
{ "connected": true, "ssid": "Home-WiFi" }
// sensors       (step_counter null if the device has no step hardware)
{ "step_counter": 18342, "steps_detected": 12, "motion_detected": false, "time": 1780473149 }
// location
{ "lat": 32.0853, "lon": 34.7818, "accuracy": 5.0, "time": 1780473149 }
// device_info
{ "time": 1780473149, "device_info": "Samsung SM-S926B (15)", "screen_locked": true }
```

### Command examples

```bash
DEV=dradis/phone        # <prefix>/<device>
mosquitto_pub -h <broker> -t $DEV/sendsms     -m '{"phone":"+972501234567","text":"hello"}'
mosquitto_pub -h <broker> -t $DEV/getlocation -m ''
mosquitto_pub -h <broker> -t $DEV/ping        -m '{"seconds":15}'      # 0 = stop
mosquitto_pub -h <broker> -t $DEV/takephoto   -m '{"camera":"rear"}'
mosquitto_pub -h <broker> -t $DEV/notify      -m '{"title":"Hi","text":"Dinner is ready"}'
mosquitto_pub -h <broker> -t $DEV/say         -m '{"text":"Dinner is ready"}'
mosquitto_pub -h <broker> -t $DEV/getstatus   -m ''
```

---

## Settings reference

**Basic** — Device name · Topic prefix.

**LAN broker** (used on a home Wi‑Fi) — Home Wi‑Fi SSIDs (comma‑separated) · Host ·
Port · Username · Password · Use TLS · CA certificate.
**WAN broker** (mobile / away) — Host · Port · Username · Password · Use TLS · CA
certificate.

> Toggling **Use TLS** swaps the port between `1883` (plain) and `8883` (TLS). For
> a self‑signed / private‑CA broker, **upload its CA certificate** (PEM) so the
> app can validate the connection; with no CA the system trust store (public CAs)
> is used.

**Update modes** — Publish periodically (heartbeat: telemetry + location) ·
Update interval (seconds).

**Outbound** — Location (enable · high accuracy) · Telemetry (enable) · Sensors
(enable) · Camera (enable · default camera front/rear).

**Inbound** — SMS (enable · notify when sent) · Notifications (enable · read aloud
via TTS) · Text‑to‑speech (enable) · Alarm/find‑phone (enable · duration · ringtone
· override silent/DND).

**Behaviour** — Autostart on boot · Reconnect on network change.

---

## Build & run

Requires **JDK 17** and the Android SDK (platform 35, build‑tools 35.x). The
Gradle wrapper pins **Gradle 8.13** — no system Gradle needed.

```powershell
# Debug APK  ->  app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat assembleDebug

# Install + launch on a connected device/emulator
.\gradlew.bat installDebug
adb shell am start -n dev.tomerklein.dradis/.MainActivity

# Static analysis
.\gradlew.bat lintDebug
```

### Signed release

Create a keystore (kept out of git) and a git‑ignored `keystore.properties` at the
repo root:

```properties
storeFile=C:/Users/you/keystores/dradis.jks
storePassword=…
keyAlias=dradis
keyPassword=…
```

```powershell
.\gradlew.bat assembleRelease -PdradisVersion=2026.6.9
# -> app\build\outputs\apk\release\app-release.apk
```

### Versioning & CI release

Versions are date‑based **`YYYY.M.PATCH`** (e.g. `2026.6.9`) computed by
`scripts/next-version.sh`; `versionCode` is derived from it (e.g. `260609`). The
`Release` GitHub Action (**Actions → Release**, `workflow_dispatch`) builds the
**signed** APK in CI (keystore from repo secrets), tags the version, and publishes
a GitHub Release with the APK attached.

Required repo secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`.

---

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | MQTT + network/SSID detection |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE`, `LOCATION`, `CAMERA`, `DATA_SYNC`) | persistent connection service |
| `SEND_SMS` | send SMS command |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | location + reading the Wi‑Fi SSID |
| `ACCESS_BACKGROUND_LOCATION` | read SSID / location while backgrounded (**"Allow all the time"**) |
| `CAMERA` | take‑photo command |
| `POST_NOTIFICATIONS` | foreground + pushed notifications |
| `ACTIVITY_RECOGNITION` | step counter / detector |
| `ACCESS_NOTIFICATION_POLICY`, `MODIFY_AUDIO_SETTINGS`, `VIBRATE` | find‑my‑phone alarm through DND |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | autostart + stay alive |

On first run, open **Status → Permissions** and grant the runtime group, then
**background location ("Allow all the time")**, **Do‑Not‑Disturb access**, and
**Ignore battery optimisation**.

---

## Device & power notes

- **Wi‑Fi SSID / LAN↔WAN switch needs background location.** Android redacts the
  SSID for a background reader unless **"Allow all the time"** is granted (plus
  location services on). Without it the SSID reads as unknown → DRADIS always
  picks the WAN broker.
- **WAN broker must be reachable off‑LAN.** A private address like `192.168.0.252`
  only works on the home network — for the WAN broker use a public IP / DDNS
  hostname (with router port‑forward) or a cloud broker, ideally over **TLS**.
- **Keep it connected (especially Samsung / One UI):** grant **Ignore battery
  optimisation**, set the app to **Unrestricted** battery usage, and add it to
  **"Never sleeping apps"** (and not "Deep sleeping apps"). In deep Doze the OS
  can still drop the socket; DRADIS reconnects within ~20 s once it can.
- **MIUI / Xiaomi:** also enable **Autostart** — `RECEIVE_BOOT_COMPLETED` alone is
  not honoured there.

---

## Security

- Prefer **TLS (8883)** for any broker reachable off your LAN — without it, SMS
  text, location and credentials travel in cleartext.
- Anyone who can publish to `<prefix>/<device>/sendsms` can send SMS and read the
  phone's location. Use **broker authentication + per‑topic ACLs** and a
  non‑guessable device name.
- Credentials and the CA certificate are stored locally via DataStore and are
  never returned in plaintext from the UI. Keystores and `keystore.properties`
  are git‑ignored.

---

## Tech stack

Kotlin · Jetpack Compose + Material 3 · HiveMQ MQTT Client (3.1.1) · CameraX ·
FusedLocationProvider · Jetpack DataStore · kotlinx.serialization · `kardianos`‑style
foreground service. minSdk 26, target/compileSdk 35, JDK 17, AGP 8.7.3 /
Gradle 8.13.

### Project layout

```
app/src/main/java/dev/tomerklein/dradis/
  MainActivity.kt            # Compose host (Status / Settings / Logs)
  mqtt/                      # MqttService, client wrapper, BrokerSelector, Topics, TLS
  commands/                  # SMS, location, ping, photo, notify, say handlers + router
  telemetry/                 # battery+wifi+device_info, sensors, periodic reporter, location
  net/NetworkMonitor.kt      # connectivity + SSID
  settings/                  # DataStore-backed settings
  ui/                        # Compose screens
  boot/BootReceiver.kt       # autostart
```
