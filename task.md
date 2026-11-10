# Aayush OS V3 (`dev.aayush.os`) — Cross-Device Intelligence Tracker

## Status Legend
- **[IMPLEMENTED & TESTED]** — Coded, deployed, and verified live on hardware (`Pixel 7 panther` × `MacBook Air M4`).
- **[PARTIALLY IMPLEMENTED / CONDITIONAL]** — Working with documented OS/hardware constraints (e.g., wireless `scrcpy` requires initial `adb tcpip 5555` bootstrap; native Android USB UVC webcam requires physical USB connection).
- **[FROZEN / PROTECTED]** — Dynamic Now Bar V2.4 preserved untouched.

---

- [x] **Phase 0: Project Audit, Stability Check & V2.4 Rollback Point** — **[IMPLEMENTED & TESTED]**
  - [x] Audited [`AAYUSH_OS_STATE.md`](file:///Users/aayushmali/Developer/Projects/AayushOS/AAYUSH_OS_STATE.md), [`MainActivity.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MainActivity.java), [`MacCompanion.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MacCompanion.java), [`aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py), and [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java).
  - [x] Verified via `adb logcat -b crash` that the earlier `RECEIVER_EXPORTED` crash from `22:17:25` was resolved and `dev.aayush.os` runs with zero crashes.
  - [x] Preserved full V2.4 rollback snapshot in [`backups/v2.4_stable_rollback/`](file:///Users/aayushmali/Developer/Projects/AayushOS/backups/v2.4_stable_rollback/).
  - [x] **Frozen Dynamic Now Bar V2.4:** Left [`NowBarOverlayService.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarOverlayService.java), [`NowBarConfig.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarConfig.java), and [`NowBarCalibrationCard.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarCalibrationCard.java) 100% untouched.

- [x] **Phase 1: MacBook Air M4 Remote Control (`MAC SYSTEM CONTROL`)** — **[IMPLEMENTED & TESTED]**
  - [x] **Mac Wi-Fi ON/OFF:** Implemented via `networksetup -setairportpower en0 on|off` with strict immediate disconnect policy (flushes HTTP 200, powers off `en0` in 80ms, sets `wifi_off_by_user=true`, never auto-restores or retries).
  - [x] **Mac Bluetooth ON/OFF:** Implemented natively via `/System/Library/Frameworks/IOBluetooth.framework/IOBluetooth` (`IOBluetoothPreferenceSetControllerPowerState` / `IOBluetoothPreferenceGetControllerPowerState`), keeping Bluetooth disabled when turned OFF with zero background override.
  - [x] **Mac Battery & Charging:** Live `pmset -g batt` parser (`%`, charging/discharging, power source, time remaining).
  - [x] **Mac CPU Usage:** Live `top -l 1 -n 0` (`user%`, `sys%`, `idle%`) + `sysctl -n vm.loadavg`.
  - [x] **Mac Uptime:** Live `uptime` parser (`up 3:58`).
  - [x] **Current Wi-Fi Network:** Live link & network state (`MALI (192.168.0.110)`).
  - [x] **Bluetooth Connection Status:** Parses `system_profiler SPBluetoothDataType` for connected audio/HID devices (`ON • Boult Audio Airbass`).
  - [x] **Lock Mac Screen:** `pmset displaysleepnow`.
  - [x] **Caffeinate Mac:** Toggleable `caffeinate -d -i -u -t 3600` with live process verification (`pgrep -x caffeinate`).
  - [x] **Mute/Unmute Mac Audio:** Live `osascript` volume & mute state reader/toggler (`Vol 69%`).
  - [x] **Dark Mode Toggle:** Live `System Events` appearance preferences reader/toggler.
  - [x] **Network Diagnostics:** Gateway (`192.168.0.1`), Pixel 7 (`192.168.0.108`), and DNS (`github.com`) latency profiler.
  - [x] **Play Notification Sound:** `afplay /System/Library/Sounds/Glass.aiff` + native macOS banner.

- [x] **Phase 2: True Wireless Ecosystem (Zero Daily USB/ADB Dependency)** — **[IMPLEMENTED & TESTED]**
  - [x] Built [`PixelCompanionServer.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java) listening on `http://192.168.0.108:8183` inside `dev.aayush.os`.
  - [x] Tested disabling ADB (`settings put global adb_enabled 0` → `adb devices` empty) and verified live over Wi-Fi that:
    - Mac ↔ Pixel status polling (`http://192.168.0.108:8183/status` & `http://192.168.0.110:8181/status`) works with ADB OFF.
    - Mac → Pixel clipboard push (`/clipboard/push-to-pixel`) works with ADB OFF.
    - Mac → Pixel file delivery (`:8183/file`) works with ADB OFF.
    - All ADB-provisioned permissions (`WRITE_SECURE_SETTINGS`, `READ_LOGS`, `SYSTEM_ALERT_WINDOW`, `BIND_NOTIFICATION_LISTENER_SERVICE`) persist in Android's package store when debugging is disabled.

- [x] **Phase 3: Control Pixel from Mac (`scrcpy 4.1` Integration)** — **[IMPLEMENTED & TESTED]**
  - [x] Integrated `/opt/homebrew/bin/scrcpy` (`v4.1`) in [`aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py) and [`CrossDeviceDeckView.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/CrossDeviceDeckView.java) + the Mac Desktop Companion Web UI (`http://127.0.0.1:8181/dashboard`).
  - [x] Supports Start/Stop mirroring, resolution (`720p`, `1080p`, `1440p`, `Native 2400p`), bitrate (`4M`, `8M`, `12M`, `16M`), Portrait/Landscape orientation, Wireless ADB bootstrap (`adb tcpip 5555` + `adb connect 192.168.0.108:5555`), Manual Reconnect, Disconnect, and Android navigation controls (`HOME`, `BACK`, `RECENTS`, `LOCK`).
  - [x] *Note:* `scrcpy` requires either Wireless ADB (`192.168.0.108:5555`) or USB ADB to be active during mirroring sessions.

- [x] **Phase 4: Pixel Camera as Mac Webcam** — **[PARTIALLY IMPLEMENTED / HARDWARE-VERIFIED]**
  - [x] Verified Pixel 7 camera hardware via `scrcpy --list-cameras`:
    - `--camera-id=0` (Rear: `4080×3072`, `15/30/60 fps`, zoom `0.67..8x`)
    - `--camera-id=1` (Front: `3440×2448`, `15/24/30 fps`, zoom `0.9..10x`)
  - [x] Integrated `scrcpy --video-source=camera` controls (`Rear 1080p 30fps`, `Rear 1080p 60fps`, `Front 1080p 30fps`, `Stop Camera`) in [`aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py) and [`CrossDeviceDeckView.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/CrossDeviceDeckView.java).
  - [x] Verified Android 17 `com.android.DeviceAsWebcam` (`/system/priv-app/DeviceAsWebcam/DeviceAsWebcam.apk`) on Pixel 7 + added 1-tap USB Settings launcher and macOS `SPCameraDataType` UVC detection indicator. Native UVC mode for FaceTime/Zoom requires a physical USB cable with USB mode set to `Webcam`.

- [x] **Phase 5: OTP Delivery to Mac Only (Never in Dynamic Now Bar)** — **[IMPLEMENTED & TESTED]**
  - [x] Updated [`NowBarState.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarState.java) and [`NowBarNotificationListener.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarNotificationListener.java) so OTPs **never** populate `NowBarState.recentOtp` or appear in the Dynamic Now Bar.
  - [x] Built **AAYUSH OS – MAC OTP INBOX** (`POST /otp`, `GET /otp`, `POST /otp/copy`, `POST /otp/clear`) with volatile RAM storage only, 1-second background expiry sweeper (`30s`/`60s`/`120s` TTL), explicit opt-in switch, per-app allowlist, notification redaction respect (`VISIBILITY_SECRET`), and 1-click Copy on Mac.

- [x] **Phase 6: Advanced Bidirectional Clipboard** — **[IMPLEMENTED & TESTED]**
  - [x] Added SHA-256 duplicate-sync prevention, sensitive content & password/API-key/OTP exclusion (`SECRET_PATTERN` & `OTP_PATTERN`), automatic sync on window focus toggle, manual Push/Pull, and volatile clipboard history with configurable retention (`15m`, `30m`, `60m`) in [`SmartClipboard.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/SmartClipboard.java) and [`MacCompanion.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MacCompanion.java).

- [x] **Phase 7: Bidirectional Wireless File Transfer** — **[IMPLEMENTED & TESTED]**
  - [x] Implemented streaming binary upload (`POST /file/upload`) and download (`GET /file/download`) in `16 KB` chunks with live progress bar (`%`), transfer speed (`KB/s`), file size formatting, Mac destination folder selection (`~/Downloads/PixelDrop`, `~/Desktop`, `~/Documents`), cancel transfer (`AtomicBoolean`), completion notifications, and transfer history.

- [x] **Phase 8: Mac Notification Centre** — **[IMPLEMENTED & TESTED]**
  - [x] Hooked [`NowBarNotificationListener.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/NowBarNotificationListener.java) to forward permitted non-OTP notifications to `POST /notifications` on Mac, with app filtering (`Messaging & Dev` vs `All Clearable`), quick copy, open URL on Mac, and remote dismissal on Pixel (`dismissNotificationByKey`).

- [x] **Phase 9: Cross-Device Command Centre** — **[IMPLEMENTED & TESTED]**
  - [x] Built dual side-by-side live telemetry panels (**PIXEL 7 PANTHER** vs **MACBOOK AIR M4**) + 10 live quick action pills at the top of the `💻 MAC M4 & WIDGETS` tab in [`CrossDeviceDeckView.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/CrossDeviceDeckView.java).

- [x] **Phase 10: Command Center Refinement** — **[IMPLEMENTED & TESTED]**
  - [x] Fixed header row title-to-pill spacing across all cards in [`MainActivity.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/MainActivity.java) while preserving Carbon Black (`#0A0A0A` / `#141414`), McLaren Papaya (`#FF8000`), `#81` Piastri identity, and keeping Dynamic Now Bar V2.4 completely untouched.

- [x] **Phase 11: Security Hardening** — **[IMPLEMENTED & TESTED]**
  - [x] Enforced RFC1918 local-subnet validation (`is_allowed_lan_ip`), `X-Aayush-Token` shared secret authentication (`HTTP 401` verified on invalid token), `shell=False` subprocess execution, strict command allowlists, sensitive clipboard exclusions, and explicit disconnect controls.

- [x] **Phase 12: Aayush OS V3.1 — Network Independence** — **[IMPLEMENTED & TESTED ACROSS MULTIPLE NETWORKS • OFFICE WI-FI LIVE TEST PENDING]**
  - [x] **Dynamic Multi-Stage Discovery (No Permanent Dependency on `192.168.0.110`):**
    - [x] Signed UDP Subnet-Directed & Broadcast Discovery on `:8182` (`HMAC-SHA256` signed JSON beacon returning `lan_ip`, `tailscale_ip`, `mdns_host`, `port`, `ts`, `sig`).
    - [x] mDNS / Bonjour Registration & Resolution (`_aayush-os._tcp` on Mac `:8181` via `/usr/bin/dns-sd -R`, `_aayush-pixel._tcp.` on Pixel `:8183` via `NsdManager`, plus `.local` resolution `Aayushs-MacBook-Air.local`).
    - [x] ARP / Hotspot Gateway Auto-Resolution (`resolve_pixel_lan_ip()` on Mac + `discoverMacViaArpTable()` & authenticated peer learning on Pixel `:8183`).
  - [x] **Automatic Subnet & IP Transition Handling:**
    - [x] Verified live across network switch from Home Wi-Fi (`MALI` `192.168.0.110` ↔ `192.168.0.108`) to Pixel Wi-Fi Hotspot (`Pixel_8972` `10.234.233.135` ↔ `10.234.233.185`, `9.2 ms` latency, zero manual IP reconfiguration required).
    - [x] Ignored cellular interfaces (`rmnet*`, `ccmni*`, `pdp*`, `clat*`) in [`HardwareMonitor.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/HardwareMonitor.java) so Wi-Fi/Hotspot LAN interfaces (`wlan*`, `ap*`, `swlan*`) are always prioritized.
  - [x] **Client Isolation, Blocked Ports & UDP Failure Detection (`runNetworkPathAudit` & `GET /network/audit`):**
    - [x] Distinguishes UDP `:8182` broadcast filtering by AP vs TCP `:8181`/`:8183` port refusal vs AP Client Isolation vs Tailscale VPN tunnel routing.
  - [x] **Live Connection Transport Status (`DIRECT LAN` / `TAILSCALE VPN` / `OFFLINE`):**
    - [x] Displayed in the Cross-Device Command Centre badges, Mac Companion telemetry card, and Mac Web Dashboard (`:8181/dashboard`).
  - [x] **Tailscale Private-Network Fallback (`100.64.0.0/10` & `fd7a:115c:a1e0::/48`):**
    - [x] Allowed Tailscale CGNAT/ULA ranges in both [`aayush_mac_bridge.py`](file:///Users/aayushmali/Developer/Projects/AayushOS/mac-companion/aayush_mac_bridge.py) and [`PixelCompanionServer.java`](file:///Users/aayushmali/Developer/Projects/AayushOS/src/dev/aayush/os/PixelCompanionServer.java).
    - [x] Added `/Applications/Tailscale.app/Contents/MacOS/Tailscale status --json` inspection, automatic Tailscale peer IP learning, and 1-tap Tailscale launcher pills on both Pixel and Mac.
  - [x] **End-to-End Authentication & Payload Encryption (`ENC_V31`):**
    - [x] Added `HMAC-SHA256` request signing (`X-Aayush-Timestamp`, `X-Aayush-Signature`) and `ENC_V31` (`SHA256-CTR` + `HMAC-SHA256` authenticated stream cipher) for sensitive `/otp` and `/clipboard` payloads.
  - [x] **Strict Disconnect Policy Preserved:**
    - [x] Network change observers and dynamic discovery explicitly respect `manuallyDisconnected` and `wifi_off_by_user` — never auto-restoring Mac Wi-Fi or Bluetooth when turned OFF by the user.
  - [x] **Explicit Separation of HTTP Companion (`:8181` ↔ `:8183`) vs ADB (`:5555` / USB):**
    - [x] Verified live with `adb devices` empty (`INACTIVE`) while Mac controls, clipboard, file transfers, OTP inbox, and `/network/audit` operate 100% wirelessly over HTTP.
  - [ ] **Actual Office Wi-Fi Live Verification:** — **[PENDING LIVE TEST ON OFFICE WI-FI]**
    - Architecture, AP Client Isolation detection, and Tailscale fallback are implemented and verified across Home Wi-Fi (`192.168.0.0/24`) and Pixel Hotspot (`10.234.233.0/24`), but office-network compatibility is **not claimed** until tested on the physical office Wi-Fi network.
