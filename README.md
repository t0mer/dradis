# DRADIS

[![Release](https://img.shields.io/github/v/release/t0mer/dradis)](https://github.com/t0mer/dradis/releases/latest)
[![License](https://img.shields.io/github/license/t0mer/dradis)](LICENSE)

**DRADIS** turns an Android phone into an MQTT endpoint you can control remotely.
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

## Table of contents

- [Screenshots](#screenshots)
- [Features](#features)
- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Installation](#installation)
- [Permissions](#permissions)
- [Settings reference](#settings-reference)
- [MQTT topic contract](#mqtt-topic-contract)
- [Custom topics](#custom-topics)
- [Home Assistant](#home-assistant)
- [Device & power notes](#device--power-notes)
- [Troubleshooting](#troubleshooting)
- [Security](#security)
- [Build & run](#build--run)
- [Tech stack](#tech-stack)
- [Contributing](#contributing)
- [License](#license)

---

## Screenshots

The app uses a **Console** Material 3 look: dark-first with a green accent, a matching light theme, and monospace for technical data. It follows the phone's light/dark setting.

### Dark

| Status | Settings | Logs | Topics |
|---|---|---|---|
| ![Status](assets/screenshots/status.png) | ![Settings](assets/screenshots/settings.png) | ![Logs](assets/screenshots/logs.png) | ![Topics](assets/screenshots/topics.png) |

| Gallery | Discover | About |
|---|---|---|
| ![Gallery](assets/screenshots/gallery.png) | ![Discover](assets/screenshots/discover.png) | ![About](assets/screenshots/about.png) |

### Light

| Status | Settings | Logs | Topics |
|---|---|---|---|
| ![Status (light)](assets/screenshots/status-light.png) | ![Settings (light)](assets/screenshots/settings-light.png) | ![Logs (light)](assets/screenshots/logs-light.png) | ![Topics (light)](assets/screenshots/topics-light.png) |

| Gallery | Discover | About |
|---|---|---|
| ![Gallery (light)](assets/screenshots/gallery-light.png) | ![Discover (light)](assets/screenshots/discover-light.png) | ![About (light)](assets/screenshots/about-light.png) |

- **Status**: live connection state (`CONNECTED` / `CONNECTING` / `DISCONNECTED` /
  `UNAUTHORIZED`), selected broker (LAN/WAN), current SSID / host, a
  start/restart‑service button, and one‑tap access to every permission DRADIS needs.
  A warning card appears when home SSIDs are configured but background location
  is not granted.
- **Settings**: grouped into **Basic · Outbound · Inbound · Integrations ·
  Behaviour**; device name, topic prefix, home SSIDs, both brokers
  (host / port / auth / TLS + CA certificate), update modes and interval, and
  each feature with its own options. Password fields are masked.
- **Logs**: a live, in‑app mirror of the most recent 200 inbound/outbound MQTT
  messages and service events, independent of `logcat`.
- **Topics**: your custom inbound topics with their type, filter, last value and
  an on/off switch; add, edit, delete, and import/export as JSON.
- **Gallery**: pictures received on custom topics of type Picture.
- **Discover** (search icon in the top bar): listens on a filter for a limited
  time and lists the topics it sees, each importable as a custom topic.
- **About** (info icon in the top bar): version, release date, author, and links
  to the GitHub repository and the issue tracker.

---

## Features

| Feature | Description |
|---|---|
| **Dual MQTT brokers** | LAN broker on a known home SSID, WAN broker otherwise; auto‑switch + auto‑reconnect on network change. If only one broker is configured, it is used on every network. |
| **Send SMS** | JSON `{phone,text}`, or the legacy Zanzito topic‑path form; long messages are split into multipart SMS; result published to `sendsms/result`; optional notification after sending. |
| **Location** | On‑demand fix, on every (re)connect, and periodic publishing on the update interval; high‑accuracy toggle. |
| **Find my phone** | Loud, looping alarm + vibration that can bypass silent / Do‑Not‑Disturb; configurable duration and **ringtone**; remote stop. |
| **Battery & charging** | Level (0–100), charging flag, charge type (AC / USB / Wireless / None). Republished when the charger is connected or disconnected. |
| **Wi‑Fi** | Connection state + SSID. |
| **Sensors** | Step counter, step detector and significant‑motion. |
| **Take photo** | Headless capture from the front or rear camera, resized to max 1280 px, JPEG quality 70, published as base64; default camera setting. |
| **Push notification** | Show a notification in the device's notification shade from a remote message; optionally read it aloud via TTS. |
| **Text‑to‑speech** | Speak a remote message aloud (device default language). |
| **Telemetry** | Battery / Wi‑Fi / device‑info / sensors / location, each on its own topic. All five are sent on connect and on every heartbeat; a charger change sends battery, Wi‑Fi and device‑info; `getstatus` sends battery, Wi‑Fi, device‑info and sensors. |
| **Home Assistant** | MQTT auto‑discovery: appears as one device with a device_tracker, sensors/binary‑sensors and command buttons. |
| **Stay connected while asleep** | Optional Wi‑Fi lock + partial wake lock so the link survives screen‑off / device sleep. |
| **Autostart** | Starts the service after boot (when enabled). |
| **Custom topics** | The **Topics** tab: define your own inbound topics (text, alarm, picture, key/value, extracted value) with notify/speak flags, and import/export them as JSON. See [Custom topics](#custom-topics). |
| **Topic discovery** | The search icon in the top bar opens **Discover**: pick a filter (default `<prefix>/#`) and a duration (max 300 s) and the app lists the topics it sees, with a short redacted preview, size and whether it was retained. **Import as custom topic** turns one into an inbound custom topic (type suggested from the payload). Opt‑in and time‑boxed; payloads are never acted on. |
| **Gallery** | The **Gallery** tab lists pictures received on custom topics of type Picture (raw JPEG/PNG or base64, ≤ 5 MB): thumbnails, full view, **share** to other apps and delete. The newest 100 are kept in app‑private storage. |
| **Encrypted settings** | Settings (incl. broker credentials and CA certificates) are encrypted at rest with AES‑256‑GCM using an Android Keystore key. |

---

## How it works

```mermaid
flowchart LR
    subgraph Phone["Android phone (DRADIS)"]
        NM[NetworkMonitor<br/>SSID + transport] --> BS[BrokerSelector]
        ST[(Settings<br/>DataStore, AES-GCM)] --> BS
        BS --> SVC[MqttService<br/>foreground service]
        SVC --> CR[CommandRouter]
        CR --> H[SMS · Location · Ping · Photo<br/>Notify · Say · GetStatus]
        TR[Telemetry / Sensor /<br/>Periodic reporters] --> SVC
    end
    SVC <-->|home SSID| LAN[(LAN broker)]
    SVC <-->|any other network| WAN[(WAN broker)]
    LAN --- HA[Home Assistant /<br/>automations]
    WAN --- HA
```

- A single **foreground service** (`MqttService`) owns the HiveMQ MQTT 3.1.1
  client for the whole app lifetime and shows a persistent notification. It is
  started with the `specialUse` foreground‑service type; camera and location run
  with while‑in‑use access when a command fires.
- **Broker selection (`BrokerSelector` + `NetworkMonitor`):** on every network
  change the current Wi‑Fi SSID is resolved; if it is in the configured **home
  SSID** list → **LAN** broker, otherwise → **WAN** broker. If only one of the two
  brokers is configured, it is used on every network. Switching networks tears
  down and reconnects to the right broker (debounced 2.5 s to ride out flaps). The
  client is only rebuilt when the selected broker actually changes.
- **SSID resolution:** the last‑known SSID is persisted and reused on a cold start,
  kept across short Wi‑Fi drops, and re‑read (up to 5 attempts, 1.5 s apart) at startup and
  whenever the app comes to the foreground (e.g. right after granting location).
- **Client ID:** a per‑install `dradis-<uuid>` is generated once and stored in
  settings; the broker session uses `dradis-<uuid>-lan` or `dradis-<uuid>-wan`. If
  the settings are cloned to another device (detected via `ANDROID_ID`), a new ID
  is generated so two phones never evict each other.
- **Reconnect:** HiveMQ auto‑reconnect with a tightened backoff (1 s → 20 s cap)
  and a 45 s keep‑alive, so transient drops recover in seconds and the
  connection survives cellular NAT timeouts. On an auth rejection
  (`NOT_AUTHORIZED` / bad username or password) the fast loop is cancelled and the
  service retries once a minute instead, so it doesn't feed broker lockouts.
- **LWT:** on connect it publishes `status=1` (retained); the Last‑Will sets
  `status=0` (retained) so subscribers learn when the phone drops off.
- **On every (re)connect:** re‑subscribes to the command topics, republishes
  `status` and `version`, republishes the Home Assistant discovery configs, and
  sends a full telemetry, sensors and location report.
- **Heartbeat:** when *periodic updates* are on, telemetry, sensors **and**
  location are published every *update interval* (default 90 s, minimum 15 s).
- Command handlers are isolated: a failure in one (e.g. camera) never drops the
  MQTT connection.

---

## Requirements

- An Android phone running **Android 8.0 (API 26)** or newer (target SDK 35 /
  Android 15). A SIM is needed for the SMS command.
- **Google Play services** (location uses the Fused Location Provider).
- An **MQTT broker** that speaks MQTT 3.1.1 (e.g. Mosquitto), optionally with TLS.
  A second, internet‑reachable broker (or the same broker on a public address) is
  needed if you want the phone reachable away from home.
- Optional: **Home Assistant** with the MQTT integration pointed at the same broker.

---

## Installation

1. Download the latest `dradis-<version>.apk` from the
   [Releases page](https://github.com/t0mer/dradis/releases/latest).
2. Open it on the phone and allow **Install unknown apps** for your browser / file
   manager when prompted.
3. Launch **Dradis**. It asks for notification permission (Android 13+) and starts
   the foreground service.
4. Open **Settings**, fill in the device name, the broker(s) and your home Wi‑Fi
   SSIDs, then tap **Save settings**.
5. On the **Status** tab, grant the permissions in this order:
   1. **Grant SMS / Location / Camera / Notifications** (the runtime group, which
      also includes physical activity for the step sensors).
   2. **Allow background location** → choose **"Allow all the time"**.
   3. **Do‑Not‑Disturb access** (lets find‑my‑phone ring through DND).
   4. **Ignore battery optimisation**.
6. Check that the Status tab shows `CONNECTED` and the expected broker (LAN/WAN).

Release APKs are signed with one release key, so newer releases install over older
ones in place.

---

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | MQTT + network/SSID detection |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE`, `LOCATION`, `CAMERA`, `DATA_SYNC`) | persistent connection service (started as `specialUse`) |
| `SEND_SMS` | send SMS command |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | location + reading the Wi‑Fi SSID |
| `ACCESS_BACKGROUND_LOCATION` | read SSID / location while backgrounded (**"Allow all the time"**) |
| `CAMERA` | take‑photo command |
| `POST_NOTIFICATIONS` | foreground + pushed notifications |
| `ACTIVITY_RECOGNITION` | step counter / detector |
| `ACCESS_NOTIFICATION_POLICY`, `MODIFY_AUDIO_SETTINGS`, `VIBRATE` | find‑my‑phone alarm through DND |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | autostart + stay alive |

On first run, open the **Status** tab and grant the runtime group, then
**background location ("Allow all the time")**, **Do‑Not‑Disturb access**, and
**Ignore battery optimisation**.

---

## Settings reference

All settings are edited on the **Settings** tab and applied when you tap
**Save settings**.

### Basic

| Setting | Default | Description |
|---|---|---|
| Device name | `phone` | `<device>` segment of every topic. |
| Topic prefix | `dradis` | `<prefix>` segment of every topic; use `zanzito` for the legacy backend. |

**LAN broker (home Wi‑Fi)**

| Setting | Default | Description |
|---|---|---|
| Home Wi‑Fi SSIDs | *(empty)* | Comma‑separated SSIDs; on any of them the LAN broker is used. |
| Host / Port | *(empty)* / `1883` | Broker address. |
| Username / Password | *(empty)* | Optional; auth is sent only when a username is set. |
| Use TLS | off | Enables TLS. |
| CA certificate | none | PEM file picked from storage (shown when TLS is on). |

**WAN broker (mobile / away)**

| Setting | Default | Description |
|---|---|---|
| Host / Port | *(empty)* / `8883` | Broker address, reachable from the internet. |
| Username / Password | *(empty)* | Optional; auth is sent only when a username is set. |
| Use TLS | on | Enables TLS. |
| CA certificate | none | PEM file picked from storage (shown when TLS is on). |

> Toggling **Use TLS** swaps the port between `1883` (plain) and `8883` (TLS) when
> the port is still at the other default. For a self‑signed / private‑CA broker,
> **upload its CA certificate** (PEM) so the app can validate the connection; with
> no CA (or an unreadable one) the system trust store (public CAs) is used.

**Update modes**

| Setting | Default | Description |
|---|---|---|
| Publish periodically | on | Heartbeat: telemetry + sensors + location every interval. |
| Update interval (seconds) | `90` | Minimum 15. |

### Outbound

| Setting | Default | Description |
|---|---|---|
| Location: Enabled | on | Publish GPS location (on demand, on connect, periodically). |
| Location: High accuracy | on | High‑accuracy (GPS) fix; off = balanced power. |
| Telemetry: Enabled | on | Battery, Wi‑Fi and device info; also gates `getstatus`. |
| Sensors: Enabled | on | Step counter, step detector, significant motion. |
| Camera: Enabled | on | Capture photos on request. |
| Camera: Default camera: rear | on | Camera used when a request doesn't name one (off = front). |

### Inbound

| Setting | Default | Description |
|---|---|---|
| SMS: Enabled | on | Send SMS on request. |
| SMS: Notify when sent | off | Post a local notification after sending. |
| Notifications: Enabled | on | Show pushed notifications. |
| Notifications: Read aloud | off | Also speak them via TTS (needs Text‑to‑speech enabled). |
| Text‑to‑speech: Enabled | on | Speak text sent to `…/say`. |
| Alarm: Enabled | on | Sound the find‑my‑phone alarm on request. |
| Alarm: Alarm duration (seconds) | `3` | Used when a `ping` payload has no `seconds`; 1–600. |
| Alarm: Ringtone | default alarm | Picked from the system ringtone picker. |
| Alarm: Override silent / DND | on | Max the alarm stream and ring through Do‑Not‑Disturb. |

### Integrations

| Setting | Default | Description |
|---|---|---|
| Home Assistant: MQTT discovery | on | Publish retained discovery configs. |
| Home Assistant: Discovery prefix | `homeassistant` | HA discovery prefix. |

### Behaviour

| Setting | Default | Description |
|---|---|---|
| Autostart on boot | on | Start the service after boot. |
| Reconnect on network change | on | Debounce network changes (2.5 s) before re‑selecting the broker. When off, the broker is re‑selected immediately, without the debounce (reconnecting is not disabled). |
| Keep connected while asleep | on | Hold a Wi‑Fi lock + partial wake lock so the MQTT link survives device sleep (costs some battery). |

---

## MQTT topic contract

Topics are `<prefix>/<device>/<leaf>`, e.g. `dradis/phone/sendsms`. Both `prefix`
(default `dradis`) and `device` (default `phone`) are configurable. All
subscriptions and publishes use **QoS 1**; `status` and `version` are retained,
everything else DRADIS publishes is not (apart from Home Assistant discovery
configs).

**Commands are events, not state:** retained messages delivered on (re)subscribe
are **ignored**, and an empty payload on `ping` or `takephoto` is ignored too (it
is how tools such as MQTT Explorer clear a retained topic). Send `{}` to use
defaults.

### Inbound: commands the app subscribes to

| Purpose | Topic | Payload |
|---|---|---|
| Send SMS (preferred) | `<prefix>/<device>/sendsms` | `{"phone":"+972…","text":"hello"}` |
| Send SMS (legacy) | `<prefix>/<device>/sendsms/<phone>` | raw string = message text |
| Get location now | `<prefix>/<device>/getlocation` | any (e.g. empty / `{}`) |
| Find phone | `<prefix>/<device>/ping` | `{"seconds":N}`; `{}` or non‑JSON → configured duration (default 3 s); `0` or negative stops; max 600 s |
| Take photo | `<prefix>/<device>/takephoto` | `{"camera":"front"\|"rear"}`; `{}` → default camera |
| Push notification | `<prefix>/<device>/notify` | `{"title":"…","text":"…","id"?:N}` or raw text (title defaults to `Dradis`; reusing an `id` replaces that notification) |
| Text‑to‑speech | `<prefix>/<device>/say` | `{"text":"…"}` or raw text |
| Force telemetry | `<prefix>/<device>/getstatus` | any (e.g. empty); publishes `battery`, `wifi`, `device_info` and `sensors` (sensors only if Sensors is enabled) |
| Control verbs | `<prefix>/<device>/cmd/<verb>` | `{}` (a blank payload is ignored). Verbs: `restart`, `reconnect`, `take_picture` (same payload as `takephoto`), `send_heartbeat`, `set_bookmark`, `send_logs`, `send_html_logs`, `stop_alarm`. Unknown verbs are logged and ignored. `restart` and `reconnect` share a 10 s cooldown. `set_bookmark` needs Location enabled and currently only records the fix in the in‑app log |

Each command is ignored (with a note in the in‑app log) when its feature is
disabled in Settings.

### Outbound: what the app publishes

| Purpose | Topic | Payload | Retained |
|---|---|---|---|
| Online status | `<prefix>/<device>/status` | `1` online / `0` LWT offline | ✅ |
| App version | `<prefix>/<device>/version` | e.g. `2026.6.12` | ✅ |
| Device info | `<prefix>/<device>/device_info` | `{time, device_info, screen_locked}` | — |
| Battery | `<prefix>/<device>/battery` | `{battery_level, charging, charge_type}` | — |
| Wi‑Fi | `<prefix>/<device>/wifi` | `{connected, ssid}` | — |
| Sensors | `<prefix>/<device>/sensors` | `{step_counter, steps_detected, motion_detected, time}` | — |
| Location | `<prefix>/<device>/location` | `{latitude, longitude, gps_accuracy, time}` | — |
| Photo | `<prefix>/<device>/photo` | `{camera, time, jpeg_b64}` | — |
| SMS result | `<prefix>/<device>/sendsms/result` | `{phone, ok, error?}` | — |
| Log dumps | `<prefix>/<device>/log_inbound`, `<prefix>/<device>/log_outbound` | text (`send_logs`) or an HTML table (`send_html_logs`) of recent inbound / outbound messages; sent on request only | — |

All `time` fields are Unix epoch **seconds**.

### Payload examples

```jsonc
// battery
{ "battery_level": 76, "charging": true, "charge_type": "USB" }   // AC|USB|Wireless|None
// wifi          (ssid is null off Wi-Fi or when unreadable)
{ "connected": true, "ssid": "Home-WiFi" }
// sensors       (step_counter null if the device has no step hardware or ACTIVITY_RECOGNITION is denied;
//                steps_detected counts since the service started; motion_detected resets after each report)
{ "step_counter": 18342, "steps_detected": 12, "motion_detected": false, "time": 1780473149 }
// location  (Home Assistant device_tracker GPS attribute names)
{ "latitude": 32.0853, "longitude": 34.7818, "gps_accuracy": 5.0, "time": 1780473149 }
// device_info
{ "time": 1780473149, "device_info": "samsung SM-S926B (15)", "screen_locked": true }
// photo
{ "camera": "rear", "time": 1780473149, "jpeg_b64": "/9j/4AAQ…" }
// sendsms/result
{ "phone": "+972501234567", "ok": false, "error": "SEND_SMS permission not granted" }
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

# Watch everything the phone publishes
mosquitto_sub -h <broker> -t "$DEV/#" -v
```

Do not publish commands with the retain flag (`-r`): retained commands are ignored.

---

## Custom topics

The **Topics** tab lets you subscribe to your own topics and choose how each message is processed (the
equivalent of Zanzito's custom topics). Each topic has a name, an MQTT **filter** (`+` and a trailing `#`
allowed), a type, and flags: *enabled*, *notify*, *speak* and a notification icon.

| Type | What a message does |
|---|---|
| **Text** | Stores the payload as the topic's last value; optionally notifies/speaks it. |
| **Alarm** | Any non‑empty message rings the find‑phone alarm for the duration set in Settings (ignored if the alarm is disabled or the duration is 0). The payload is not interpreted. |
| **Picture** | Payload is a raw JPEG/PNG, base64, or `{"jpeg_b64":"…"}` (≤ 5 MB). Saved to the **Gallery**; notified only if *notify* is on. |
| **Key/Value** | Splits the payload at the first separator (default `:`) into `label` and `value`. |
| **Extracted** | Pulls one value out of the payload with a JSONPath subset (`$.a.b[0]`, `$['key']`) or a regex (group 1 if the pattern has one, else the whole match). |

Rules worth knowing:

- Retained messages and empty payloads are ignored (commands are events, not state).
- A filter may not overlap the app's own command or telemetry topics, nor another custom topic; built‑in
  commands always win. Up to 100 topics; at most one message per topic every 2 s.
- Notification titles are always the topic's name, never taken from the payload. The global notification,
  speech and alarm switches in Settings still apply.
- Regexes run under a 100 ms budget and only on the first 8 KB of a payload.
- **Discover** (search icon in the top bar) listens on a filter for a limited time and can import a seen
  topic as a custom topic.
- **Export** writes all topics to a JSON file; **Import** appends the topics of such a file (a bare JSON
  array also works). Imported topics get new ids and arrive **disabled** (switch them on after checking them), and the whole file is rejected if any entry is invalid or
  collides with an existing topic. Files over 256 KB are refused.

```json
{ "version": 1, "topics": [
  { "id": "…", "name": "Door", "filter": "home/door/state", "type": "TEXT", "notify": true }
] }
```

### Outbound topics (publish a device value)

Under **Publish** on the Topics tab you can make the phone publish one device value to a topic of your choice,
on connect, whenever the list changes and on every update interval.

| Value | Payload | Gated by |
|---|---|---|
| Battery level | `87` | Telemetry switch |
| Charging | `true` / `false` | Telemetry switch |
| Wi‑Fi SSID | the SSID, or `none` when not on Wi‑Fi (never an empty payload, which would clear a retained topic) | Telemetry switch |
| Location | the same JSON as the `location` topic (`latitude`, `longitude`, `gps_accuracy`, `time`); nothing is sent without a fix | Location switch and permission |

Each entry can be *retained* and enabled or disabled. A published topic cannot contain `+` or `#`, cannot start
with `$`, cannot be under your topic prefix (that namespace belongs to dradis devices) or a topic the app uses itself, and cannot be covered by one of your inbound custom filters
(and an inbound filter cannot cover an outbound topic): that would echo the value back into the app. Up to 50
entries. They are part of the JSON export/import (imported entries start disabled).

The list is stored encrypted on the device; it is not included in backups (`allowBackup="false"`).

---

## Home Assistant

DRADIS supports **MQTT auto‑discovery**. With **Settings → Home Assistant → MQTT
discovery** enabled (default), it publishes retained config topics under the
discovery prefix (default `homeassistant`) on every connect and settings save, so
HA auto‑creates a single **DRADIS &lt;device&gt;** device with:

| Entity | Type | Source |
|---|---|---|
| Location | `device_tracker` (GPS) | `…/location` (`latitude`/`longitude`/`gps_accuracy`) |
| Battery | `sensor` (battery %, measurement) | `…/battery` |
| Charging | `binary_sensor` (battery_charging) | `…/battery` |
| Charge type | `sensor` | `…/battery` |
| Wi‑Fi | `binary_sensor` (connectivity) | `…/wifi` |
| SSID | `sensor` | `…/wifi` |
| Steps | `sensor` (total_increasing) | `…/sensors` |
| Motion | `binary_sensor` (motion) | `…/sensors` |
| Find phone | `button` | `…/ping` with `{"seconds":30}` |
| Take photo | `button` | `…/takephoto` with `{"camera":"rear"}` |
| Update location | `button` | `…/getlocation` |
| Refresh status | `button` | `…/getstatus` |

Config topics follow `<discovery prefix>/<component>/dradis_<device>/<object>/config`.
Entity availability follows the retained `…/status` topic (LWT), so the device
shows **unavailable** when the phone drops off. Requires Home Assistant's
**MQTT integration** pointed at the same broker. Disabling the toggle clears the
discovery configs (empty retained payloads).

---

## Device & power notes

- **Wi‑Fi SSID / LAN↔WAN switch needs background location.** Android redacts the
  SSID for a background reader unless **"Allow all the time"** is granted (plus
  location services on). Without it the SSID reads as unknown → DRADIS picks the
  WAN broker.
- **WAN broker must be reachable off‑LAN.** A private address like `192.168.0.252`
  only works on the home network. For the WAN broker, use a public IP / DDNS
  hostname (with router port forwarding) or a cloud broker, ideally over **TLS**.
- **Keep it connected (especially Samsung / One UI):** keep **Keep connected while
  asleep** on, grant **Ignore battery optimisation**, set the app to
  **Unrestricted** battery usage, and add it to **"Never sleeping apps"** (and not
  "Deep sleeping apps"). In deep Doze the OS can still drop the socket; DRADIS
  reconnects within ~20 s once it can.
- **MIUI / Xiaomi:** also enable **Autostart**, because `RECEIVE_BOOT_COMPLETED`
  alone is not honoured there.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Status `DISCONNECTED` with detail *Broker not configured* | Neither broker has a host set. Fill in at least one broker and save. |
| Status `UNAUTHORIZED` / notification *Not authorized — check broker credentials* | The broker rejected the username/password (or temporarily locked the client out). DRADIS retries once a minute; fix the credentials and save to reconnect immediately. |
| Always on the WAN broker at home | The SSID can't be read: grant **"Allow all the time"** location, turn location services on, and check the SSID spelling in **Home Wi‑Fi SSIDs**. |
| Phone goes offline when the screen is off | Enable **Keep connected while asleep** and apply the battery settings in [Device & power notes](#device--power-notes). |
| Find‑my‑phone is silent in DND | Grant **Do‑Not‑Disturb access** and keep **Override silent / DND** on. |
| `sendsms/result` has `"error": "SEND_SMS permission not granted"` or `"missing phone or text"` | Grant the SMS permission, or fix the payload. |
| `step_counter` is `null` | The device has no step sensor or **Physical activity** permission is denied. |
| A command does nothing | Check the feature is enabled in Settings, that it wasn't published retained, and look at the **Logs** tab for the reason. |
| "App not installed" when updating | The installed APK was signed with a different key (e.g. a local debug build). Uninstall first; this wipes the settings. |

---

## Security

- Prefer **TLS (8883)** for any broker reachable off your LAN. Without it, SMS
  text, location and credentials travel in cleartext.
- Anyone who can publish to `<prefix>/<device>/…` can send SMS, trigger the camera
  and read the phone's location. Use **broker authentication + per‑topic ACLs** and
  a non‑guessable device name.
- Settings, including broker credentials and CA certificates, are stored in
  DataStore **encrypted with AES‑256‑GCM** using a non‑exportable Android Keystore
  key. Password fields are masked in the UI. App backup is disabled
  (`allowBackup="false"`).
- The in‑app log masks phone‑number‑like digit runs in inbound topics/payloads and
  truncates inbound payloads to 300 characters.
- `send_logs` / `send_html_logs` leave the device, so each dumped entry is
  redacted (digit runs of 7+ are masked), flattened to one line and truncated to
  300 characters. Anyone who can subscribe to the log topics can still read this
  activity; restrict them with ACLs.
- `cmd/<verb>` (restart, reconnect, stop_alarm, send_logs, …) has no per‑sender
  check: the broker's authentication and ACLs are the only access control.
- Received pictures are stored in app‑private storage under random file names (never taken from a payload). Sharing goes through a `FileProvider` that exposes only the gallery folder, and only to the app the user picks.
- Topic discovery subscribes to a filter only while a session you started is running (clean session, ended on timeout, Stop or disconnect). Discovered payloads are not routed to any handler, are never written to the in‑app log, and appear only as redacted previews of at most 80 characters. Up to 200 topics are kept. Because a broad filter overlaps the command topics, identical messages arriving back‑to‑back are collapsed so a command is not run twice.
- Keystores and `keystore.properties` are git‑ignored.

---

## Build & run

Requires **JDK 17** and the Android SDK (platform 35, build‑tools 35.x). The
Gradle wrapper pins **Gradle 8.13**, so no system Gradle is needed. On Linux/macOS
use `./gradlew` instead of `.\gradlew.bat`.

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

Alternatively, set the environment variables `DRADIS_KEYSTORE_FILE`,
`DRADIS_KEYSTORE_PASSWORD`, `DRADIS_KEY_ALIAS` and `DRADIS_KEY_PASSWORD`; they
take precedence over `keystore.properties`.

```powershell
.\gradlew.bat assembleRelease -PdradisVersion=2026.6.9
# -> app\build\outputs\apk\release\app-release.apk
```

Release builds are minified with R8. When a release key is configured, **debug
builds are signed with it too**, so debug and release APKs can update each other in
place. Without it, debug builds use the default per‑machine debug key.

### Versioning & CI release

Versions are date‑based **`YYYY.M.PATCH`** (e.g. `2026.6.9`) computed by
`scripts/next-version.sh`; `versionCode` is derived from it (e.g. `260609`). Local
builds without `-PdradisVersion` are versioned `dev` with `versionCode` 1, which
can't be installed over a dated release. The `Release` GitHub Action
(**Actions → Release**, `workflow_dispatch`, optional `version` input) builds the
**signed** APK in CI (keystore from repo secrets), tags the version, and publishes
a GitHub Release with `dradis-<version>.apk` attached.

Required repo secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`.

Dependabot checks Gradle dependencies and GitHub Actions weekly.

---

## Tech stack

Kotlin · Jetpack Compose + Material 3 · HiveMQ MQTT Client (3.1.1) · CameraX ·
FusedLocationProvider · Jetpack DataStore + Android Keystore · kotlinx.serialization ·
foreground service. minSdk 26, target/compileSdk 35, JDK 17, AGP 8.7.3 /
Gradle 8.13.

### Project layout

```
app/src/main/java/dev/tomerklein/dradis/
  MainActivity.kt            # Compose host (Status / Settings / Logs / About)
  DradisApp.kt, ServiceLocator.kt  # app entry point + manual DI
  mqtt/                      # MqttService, client wrapper, BrokerSelector, Topics, TLS,
                             # HA discovery, wake/Wi-Fi locks
  commands/                  # SMS, location, ping, photo, notify, say, getstatus handlers + router
  telemetry/                 # battery+wifi+device_info, sensors, periodic reporter, location
  net/NetworkMonitor.kt      # connectivity + SSID
  settings/                  # DataStore-backed, encrypted settings + client ID
  log/MqttLog.kt             # in-app log ring buffer
  ui/                        # Compose screens
  boot/BootReceiver.kt       # autostart
```

---

## Contributing

Bug reports and pull requests are welcome. Open an issue at
[github.com/t0mer/dradis/issues](https://github.com/t0mer/dradis/issues) (the
**About** tab links there too). For code changes, keep every topic string in
`mqtt/Topics.kt`, and run `lintDebug` and a release build (`assembleRelease`) before
opening a PR.

---

## License

Licensed under the [Apache License 2.0](LICENSE).
